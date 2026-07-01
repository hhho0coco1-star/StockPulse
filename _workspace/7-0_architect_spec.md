# Phase 7 — 부하/장애 테스트 구현 스펙 (architect)

> 대상: backend-dev. 이 문서가 구현 근거다.
> 전제: Phase 6까지 완료 (13개 서비스 Micrometer 계측, Prometheus/Grafana/Loki/Tempo 가동).
> WBS 05 Phase 7 DoD: "k6 시나리오 + 시세 증폭 처리량 측정 + Chaos Mesh 장애 주입 + 결과·병목·개선 문서화(10·11·12)".

## 0. 엔드포인트 계약 정정 (중요)

작업 지시서의 경로명과 실제 API 설계(`docs/03_API설계.md`)가 다르다. **실제 계약을 따른다.** 모든 경로는 api-gateway(:8080) 기준 `/api/v1` prefix.

| 작업지시 표기 | 실제 계약 (구현 기준) | 서비스 | 비고 |
|---|---|---|---|
| `GET /api/v1/quotes/{symbol}` | `GET /api/v1/market/quote/{symbol}` | Market (:8082) | 현재가 1건, Redis 캐시 |
| `POST /api/v1/orders` | `POST /api/v1/orders` | Trading (:8086) | 동일. 202 Accepted, Saga 비동기 |
| `POST /api/v1/posts` | `POST /api/v1/community/{symbol}/posts` | Community (:8091) | body `{ "content" }` |

> k6 스크립트의 URL은 위 "실제 계약" 열을 그대로 사용한다. 작업지시 표기를 쓰면 404가 난다.

인증: 모든 요청 `Authorization: Bearer ${K6_JWT_TOKEN}`. Gateway가 JWT 검증 후 다운스트림에 `X-User-Id` 전달(SafeAlert 패턴).

---

## §1. k6 부하 테스트 스크립트

### 1.1 디렉터리 구조
```
tests/load/
├── lib/
│   ├── config.js          # BASE_URL, JWT, 공통 thresholds, headers 헬퍼
│   └── symbols.js         # 테스트 종목 풀 (005930, 000660, 035420, 005380, 051910 …)
├── scenarioA_quote.js     # 시세 조회 (read-heavy)
├── scenarioB_order.js     # 모의투자 주문 (write, Saga)
├── scenarioC_post.js      # 토론방 게시글 작성 (write)
├── all.js                 # 3 시나리오 동시 실행(혼합 부하) — scenarios 블록 통합
└── README.md              # 실행법, 환경변수, 결과 해석
```

### 1.2 공통 모듈 `lib/config.js`
```javascript
export const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const JWT = __ENV.K6_JWT_TOKEN;           // 필수. 없으면 setup에서 fail
export function authHeaders() {
  return { headers: { Authorization: `Bearer ${JWT}`, 'Content-Type': 'application/json' } };
}
// 공통 threshold (각 스크립트에서 spread)
export const commonThresholds = {
  http_req_failed: ['rate<0.01'],          // error_rate < 1%
  http_req_duration: ['p(95)<500'],        // p95 < 500ms
};
```
> `K6_JWT_TOKEN` 미설정 시 `setup()`에서 `fail('K6_JWT_TOKEN required')`로 즉시 중단(가짜 401 부하 방지).

### 1.3 시나리오별 명세

#### (A) `scenarioA_quote.js` — 시세 조회 `GET /api/v1/market/quote/{symbol}`
- 성격: read-heavy, Redis 캐시 hit 경로. 가장 높은 RPS 목표.
- executor: `ramping-arrival-rate` (RPS 기반 — 도착률 고정이 처리량 측정에 정확).
- 단계:
  | stage | duration | target RPS |
  |---|---|---|
  | ramp-up | 30s | 0 → 500 |
  | sustain | 2m | 500 |
  | peak | 1m | 500 → 1000 |
  | ramp-down | 30s | 1000 → 0 |
- preAllocatedVUs: 100, maxVUs: 400.
- 종목은 `symbols.js` 풀에서 랜덤 선택(캐시 분산).
- thresholds: `...commonThresholds` + `http_req_duration: ['p(95)<300']` (캐시 경로라 더 빡빡).
- check: status 200, body `success===true`, `data.price` 존재.

#### (B) `scenarioB_order.js` — 모의투자 주문 `POST /api/v1/orders`
- 성격: write, 202 Accepted + Saga 비동기. 동기 응답만 측정(체결은 WS 통지라 k6 범위 밖).
- executor: `ramping-vus` (주문은 VU당 think-time 있는 사용자 행동 모델).
- 단계:
  | stage | duration | target VUs |
  |---|---|---|
  | ramp-up | 30s | 0 → 50 |
  | sustain | 2m | 50 |
  | peak | 1m | 50 → 100 |
  | ramp-down | 30s | 100 → 0 |
- 목표 RPS: 약 50~80 (VU당 sleep 1s 가정).
- payload: `{ symbol, side:"BUY", type:"MARKET", quantity: 1 }` (LIMIT은 price 추가).
- thresholds: `...commonThresholds` + `http_req_duration: ['p(95)<500']`. 정상 응답코드 **202** 임을 check (200 아님).
- check: status 202, `data.orderId` 존재. sleep(1).
- 주의: 잔고 부족(`ORDER_INSUFFICIENT_BALANCE`, 409)은 error_rate에서 제외해야 함 → `http_req_failed`는 4xx도 실패로 카운트하므로, 별도 `Counter`(business_rejected)로 분리하고 setup에서 `POST /api/v1/account/reset`로 초기자금 충전.

#### (C) `scenarioC_post.js` — 토론방 게시글 `POST /api/v1/community/{symbol}/posts`
- 성격: write, MongoDB + Outbox. 중간 RPS.
- executor: `ramping-vus`.
- 단계:
  | stage | duration | target VUs |
  |---|---|---|
  | ramp-up | 30s | 0 → 30 |
  | sustain | 2m | 30 |
  | ramp-down | 30s | 30 → 0 |
- payload: `{ content: "load test ${__VU}-${__ITER} ${Date.now()}" }`.
- thresholds: `...commonThresholds` (p95<500, err<1%).
- check: status 200/201, `data.postId` 존재. sleep(1).

### 1.4 혼합 부하 `all.js`
- 3 시나리오를 단일 `options.scenarios`에 `startTime` 다르게 배치(A 즉시, B +10s, C +20s)해 실서비스 유사 혼합 트래픽 재현.
- summary는 시나리오별 태그(`{ scenario:'quote' }`)로 분리 집계.

### 1.5 실행 커맨드 (README.md 기재)
```bash
export K6_JWT_TOKEN="<로그인으로 발급한 access token>"
export K6_BASE_URL="http://localhost:8080"
k6 run tests/load/scenarioA_quote.js
k6 run tests/load/scenarioB_order.js
k6 run tests/load/scenarioC_post.js
k6 run tests/load/all.js
# 결과 JSON 저장: k6 run --summary-export=tests/load/out/A.json scenarioA_quote.js
```

---

## §2. Chaos Mesh 장애 주입 YAML

### 2.1 디렉터리 구조
```
tests/chaos/
├── pod-kill.yaml                 # 시나리오 1
├── network-delay.yaml            # 시나리오 2
├── kafka-partition-offline.yaml  # 시나리오 3
└── README.md                     # apply/검증/teardown 절차
```
- 네임스페이스: `stockpulse`. 모든 selector는 `app.kubernetes.io/name` 라벨 기준(Helm 표준 라벨, Phase 8과 정합).
- 전제: 클러스터에 Chaos Mesh 설치(`kubectl get pods -n chaos-mesh`). 미설치 시 dry-run만 가능 → DoD §3은 dry-run으로 충족.

### 2.2 시나리오 1 — Pod kill: `pod-kill.yaml`
- 목적: trading-service Pod 강제 종료 → 즉각 재시작(Deployment 자기치유) 확인.
- 종류: `PodChaos`, action `pod-kill`.
```yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: trading-pod-kill
  namespace: stockpulse
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces: [stockpulse]
    labelSelectors:
      app.kubernetes.io/name: trading-service
  # 일회성. 반복 검증은 schedule로 별도 작성.
```
- 검증: `kubectl get pods -n stockpulse -l app.kubernetes.io/name=trading-service -w` 로 Terminating→Running 관찰, MTTR(종료~Ready) 측정. 진행 중 주문 요청은 5xx 후 재시도로 회복.

### 2.3 시나리오 2 — Network delay: `network-delay.yaml`
- 목적: market-collector → Redis 200ms 지연 주입 → Resilience4j Circuit Breaker `CLOSED→OPEN→HALF_OPEN` 전이 확인.
- 종류: `NetworkChaos`, action `delay`. direction `to`, target=Redis Pod.
```yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: collector-redis-delay
  namespace: stockpulse
spec:
  action: delay
  mode: all
  selector:                       # 지연을 겪는 주체
    namespaces: [stockpulse]
    labelSelectors:
      app.kubernetes.io/name: market-collector
  direction: to
  target:                         # 지연 대상(Redis)
    mode: all
    selector:
      namespaces: [stockpulse]
      labelSelectors:
        app.kubernetes.io/name: redis
  delay:
    latency: '200ms'
    jitter: '20ms'
    correlation: '50'
  duration: '90s'
```
- 검증: Resilience4j 타임아웃(예: 100ms) < 주입 지연(200ms) 이어야 CB가 열림. Grafana `resilience4j_circuitbreaker_state` 또는 Actuator `/actuator/circuitbreakers`에서 OPEN→HALF_OPEN 전이 관찰. duration 종료 후 CLOSED 복귀 확인.

### 2.4 시나리오 3 — Kafka 격리: `kafka-partition-offline.yaml`
- 목적: Kafka 브로커 네트워크 격리(`partition`) → producer 발행 실패 → 재시도 소진 후 DLT(Dead Letter Topic) 적재 확인.
- 종류: `NetworkChaos`, action `partition` (broker를 나머지로부터 양방향 격리).
```yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: kafka-partition-offline
  namespace: stockpulse
spec:
  action: partition
  mode: all
  selector:                       # 격리 대상: kafka
    namespaces: [stockpulse]
    labelSelectors:
      app.kubernetes.io/name: kafka
  direction: both
  target:                         # 격리 상대: kafka 외 전체 (consumer/producer 서비스)
    mode: all
    selector:
      namespaces: [stockpulse]
      expressionSelectors:
        - key: app.kubernetes.io/name
          operator: NotIn
          values: [kafka]
  duration: '60s'
```
- 검증: 격리 중 producer 로그에 발행 실패/재시도, 복구 후 DLT 토픽(`<topic>.DLT`) 메시지 적재 확인(§4-5). consumer는 격리 해제 후 lag 회복.

### 2.5 apply / teardown (README.md)
```bash
kubectl apply -n stockpulse --dry-run=client -f tests/chaos/pod-kill.yaml         # DoD §3
kubectl apply -n stockpulse -f tests/chaos/pod-kill.yaml
kubectl get podchaos,networkchaos -n stockpulse
kubectl delete -f tests/chaos/pod-kill.yaml                                       # teardown
```

---

## §3. 결과 문서 갱신 내용

### 3.1 `docs/10_부하테스트_결과.md`
기존 골격(테스트환경/시나리오/결과/병목) 유지하고 아래로 채운다.
- **테스트 환경**: 실행 위치(로컬 docker-compose vs K8s), CPU/RAM, 서비스 인스턴스 수, k6 버전, JWT 발급 방법.
- **시나리오별 결과 테이블** (시나리오 A/B/C 각 행):
  | 시나리오 | 목표 RPS | 달성 RPS | p95(ms) | p99(ms) | error_rate | 판정 |
  |---|---|---|---|---|---|---|
  | A 시세조회 | 1000 | … | … | … | … | PASS/FAIL |
  | B 주문 | 80 | … | … | … | … | … |
  | C 게시글 | 30 | … | … | … | … | … |
- **병목 분석**: Grafana/Tempo 근거로 어느 구간(Gateway/DB/Kafka/Redis)에서 지연 발생했는지, 개선안(커넥션풀, 캐시 TTL, HPA 임계). 실부하 미실행 시 "추정치(인프라 기준)"임을 주석.

### 3.2 `docs/11_장애테스트_결과.md`
기존 시나리오 테이블의 "결과" 열을 채우고 아래 섹션 추가.
- **시나리오별 결과**:
  | 시나리오 | 주입 | MTTR | CB 전이 | DLT 건수 | 보상 트랜잭션 | 판정 |
  |---|---|---|---|---|---|---|
  | trading Pod kill | PodChaos | …s | - | - | - | … |
  | collector↔Redis 지연 | NetworkChaos delay | - | CLOSED→OPEN→HALF_OPEN→CLOSED | - | - | … |
  | Kafka 격리 | NetworkChaos partition | …s | - | N건 | 성공/실패 | … |
- **관찰 결과**: CB 전이 그래프 캡처 위치, 이벤트 무유실 근거(offset/DLT), Saga 보상 성공 여부.

### 3.3 `docs/12_트러블슈팅_기록.md`
기존 양식(증상/원인/해결/배운점) 사용. Phase 1~6 **실제** 사례를 기록(가공 금지 — 실제 발생분만). 후보 영역(코드/커밋/PR에서 확인 후 작성):
- Kafka KRaft 초기 클러스터ID/리스너 설정 이슈
- Gateway JWT 필터 ↔ 다운스트림 `X-User-Id` 전달 누락
- TimescaleDB hypertable/연속집계 마이그레이션
- Saga 멱등성(`processed_event`) 중복 처리
- Resilience4j 타임아웃 vs CB 임계 튜닝(Phase 5)
- OTel/Tempo 트레이스 컨텍스트 전파(Phase 6)
> backend-dev는 추측으로 채우지 말고, 실제 커밋/이슈/로그에서 확인된 것만 기록. 확인 불가 시 "기록 대기" 표시.

---

## §4. DoD (검증 방법 포함)

| # | 항목 | 검증 방법 |
|---|---|---|
| 1 | k6 3개 스크립트 실행 가능 | `k6 run tests/load/scenarioA_quote.js` (B,C 동일) 가 스크립트 파싱·실행 진입 성공. 구문검증: `k6 inspect <file>` 또는 `k6 run --vus 1 --iterations 1`. |
| 2 | p95 < 500ms threshold PASS | k6 summary에서 `http_req_duration p(95)<500` threshold ✓. 실부하 미가동 시 `--iterations 1` 스모크 통과 + 문서에 "추정 주석" 명시. |
| 3 | Chaos YAML kubectl apply 가능 | `kubectl apply --dry-run=client -f tests/chaos/<each>.yaml` 3종 모두 `created (dry run)` 출력, 스키마 오류 없음. |
| 4 | CB OPEN 시 503 응답 확인 | network-delay 주입 중 해당 경로 호출 시 Gateway/서비스가 503 + `error.code=UPSTREAM_UNAVAILABLE` 반환(03_API §1.3). 로그/Actuator `circuitbreakers` state=OPEN 캡처. |
| 5 | DLT 메시지 적재 확인 | kafka 격리·복구 후 `kafka-console-consumer --bootstrap-server localhost:9092 --topic <topic>.DLT --from-beginning` 로 메시지 ≥1건 확인. |
| 6 | 문서 3종 갱신 완료 | `docs/10·11·12` 가 골격 placeholder 제거되고 §3 항목 채워짐. 미측정 항목은 "추정/대기"로 명시(공란 금지). |

### 산출물 체크리스트 (backend-dev 제출물)
- [ ] `tests/load/` (lib 2 + 스크립트 4 + README)
- [ ] `tests/chaos/` (YAML 3 + README)
- [ ] `docs/10_부하테스트_결과.md` 갱신
- [ ] `docs/11_장애테스트_결과.md` 갱신
- [ ] `docs/12_트러블슈팅_기록.md` 갱신
- [ ] WBS `05` Phase 7 4항목 `[x]`, `todo.md` 동기화

### 구현 주의 (얕은 함정)
- 주문 정상 응답은 **202**다. k6 check를 200으로 두면 전부 실패로 잡힌다.
- 주문 부하 전 `POST /api/v1/account/reset` 으로 잔고 충전. 안 하면 409 폭증.
- CB가 열리려면 **R4j 타임아웃 < 주입 지연(200ms)** 이어야 함. 설정 확인 후 필요시 지연값 상향.
- Chaos Mesh 미설치 환경에서는 DoD #4·#5는 "로컬 docker-compose 대체 검증"(컨테이너 stop으로 유사 재현) 경로를 README에 함께 기재.
