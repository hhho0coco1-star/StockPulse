# StockPulse WBS / 개발 계획

> Phase 0~9를 실행 가능한 작업 단위로 분해. 각 Phase는 **완료 기준(DoD)** 충족 시 다음으로. 실시간 진행 체크는 [todo.md](../todo.md) 사용. 전략: **Phase 1~3에서 "시세→인사이트→모의매매" 수직 슬라이스를 먼저 완성**해 실사용 가능 상태를 조기 확보.

---

## 1. 마일스톤 개요

| Phase | 목표 | 핵심 산출물 | 완료 기준(DoD) |
|-------|------|------------|---------------|
| 0 | 기반 | 모노레포·인프라·API키 검증·Gateway/Auth | 회원가입·로그인 동작, 인프라 기동, 외부 API 3종 호출 성공 |
| 1 | 시세 파이프라인 | Market Collector→Kafka→Redis→WS, 시세판 | 화면에서 실시간 시세 갱신 확인 |
| 2 | 인사이트 엔진 | News/Fundamentals Collector + Insight | 종목 전망 카드(점수·근거) 조회·갱신 |
| 3 | 모의투자 Saga | Trading·Account·Portfolio·Ranking | 매수→체결→보유·잔고 반영, 실패 시 보상 롤백 확인 |
| 4 | 커뮤니티·실용 | 토론방·채팅, 워치리스트, 알림+FCM | 채팅 송수신, 목표가 알림 푸시 수신 |
| 5 | 안정성 | CB/retry/timeout, 장애 대응 | 외부 API/Kafka/Redis 다운 시 서비스 지속 |
| 6 | 관측 | Prometheus·Grafana·Loki·Tempo | 대시보드·트레이스로 end-to-end 확인 |
| 7 | 부하/장애 테스트 | k6·시세증폭, Chaos Mesh | 목표 처리량 측정, 장애 시나리오 문서화 |
| 8 | 배포/가용성 | K8s+HPA, Helm, GitHub Actions, 웹 배포 | 클라우드 접속 가능, HPA 스케일아웃 확인 |
| 9 | 앱 | React Native(Expo) + FCM | 앱에서 시세·인사이트·푸시 동작 |

---

## 2. Phase별 작업 분해 (WBS)

### Phase 0 — 기반 ✅ 완료
- [x] 모노레포 디렉토리 구조 + Gradle 멀티모듈 세팅 (WBS 0-1, 2026-06-22)
- [x] `docker-compose.yml`: Kafka(KRaft)·Redis·PostgreSQL·TimescaleDB·MongoDB (WBS 0-2, 2026-06-22)
- [x] 외부 API 키 발급(KIS·네이버·DART) — **사용자** (WBS 0-3, 2026-06-29)
- [x] 키로 실제 1회 호출 검증 (WBS 0-4, 2026-06-29)
- [x] API Gateway(Spring Cloud Gateway): JWT 필터·라우팅 골격 (WBS 0-5, 2026-06-29)
- [x] Auth/User: 회원가입·로그인 엔드포인트 골격 (WBS 0-5, 2026-06-29)
- [x] 공통 모듈: ApiResponse 응답/에러 포맷 (WBS 0-5, 2026-06-29)
- [x] README 골격 + GitHub Actions CI (WBS 0-6)

### Phase 1 — 시세 파이프라인 ✅ 완료
- [x] Market Collector: KIS WS 수신(Coroutines) → `market.tick` 발행
- [x] 시세 리플레이/증폭 모드(부하·장 마감 시간용)
- [x] TimescaleDB 적재 + 캔들 연속 집계
- [x] Redis 시세 캐시(`quote:{symbol}`)
- [x] Realtime Gateway: `/topic/market/{symbol}` WS 푸시 + Redis Pub/Sub
- [x] Market REST: 종목 검색·현재가·캔들 조회
  - 검증: ws://ops.koreainvestment.com:31000 삼성전자 실시간 틱 수신 + TimescaleDB 적재 확인 (2026-06-30)

### Phase 2 — 인사이트 엔진 ✅ 완료
- [x] News Collector: 네이버 뉴스 수집 → `news.raw`
- [x] Fundamentals Collector: DART 재무 수집 → `fundamentals.raw`
- [x] Insight Service: Kafka Streams 윈도우 집계(모멘텀) + 실적 종합
- [x] 밸류에이션 축: PER·PBR·PEG 계산 + 업종평균·과거밴드 대비 상대 위치
- [x] 감성 분석: 룰/사전 기반(키워드 분류) 채택
- [x] 4축 종합 전망 카드 MongoDB 저장 + `insight.updated` 발행
- [x] 백테스트: 과거 점수 vs 이후 수익률 적중률 집계·저장
- [x] Insight REST(카드·근거·백테스트) + Realtime 인사이트 푸시
  - ⚠️ fundamentals-collector 런타임 검증 별도 필요 (cron 매일 02:00)

### Phase 3 — 모의투자 Saga ✅ 완료
- [x] Account: 잔고·원장·Outbox
- [x] Portfolio: 보유종목·평가·Outbox
- [x] Trading: 주문 접수·Saga 오케스트레이터·order_saga 상태
- [x] Saga 흐름: 예약→체결→보유갱신→확정 + 단계별 보상
- [x] 멱등성(processed_event) 처리
- [x] Ranking: Redis Sorted Set 수익률 랭킹

### Phase 4 — 커뮤니티·실용 기능 ✅ 완료
- [x] Community(Discussion): 게시글·댓글·좋아요 + Outbox (:8091)
- [x] Realtime 채팅: `/app/chat/{symbol}` → `/topic/chat/{symbol}` fan-out
- [x] Watchlist-service: 워치리스트 CRUD (:8092, Auth에서 분리)
- [x] Notification/Alert: 알림 규칙·디바이스 토큰·이력 (:8093)
- [x] FCM 연동 + 규칙 평가(목표가·급등락·뉴스)

### Phase 5 — 안정성 ✅ 완료
- [x] 외부 API 전부 Resilience4j(CB·retry·timeout·bulkhead)
- [x] Kafka DLT(Dead Letter Topic) + Redis fallback
- [x] Saga 보상 실패·교착 처리(타임아웃·DLQ)
- [x] GlobalExceptionHandler(6개 서비스) + 요청 검증 표준화

### Phase 6 — 관측 ✅ 완료
- [x] OpenTelemetry 계측 → Tempo(트레이스), OTLP HTTP 4318
- [x] Prometheus 메트릭 + Grafana 대시보드
- [x] Loki 로그 수집 (loki4j appender)
- [x] 14개 서비스 Micrometer 계측 + reactive 3개 Reactor Context 전파

### Phase 7 — 부하/장애 테스트 ✅ 완료 (스크립트·문서 기준)
- [x] k6 시나리오 4종 (scenarioA 시세·B 주문·C 게시글·all 혼합)
- [x] Chaos Mesh YAML 3종 (pod-kill·network-delay·kafka-partition-offline)
- [x] 결과·병목·개선 문서화 (docs/10·11·12)
  - ⚠️ 실부하 미실행 — K8s 이미지 배포 후 재측정 필요

### Phase 8 — 배포/가용성 ✅ 완료
- [x] Helm 단일 차트 (services range, Bitnami subchart dependency 5종)
- [x] 14개 서비스 Dockerfile (멀티스테이지 gradle:8.10-jdk21 → temurin:21-jre-alpine)
- [x] GitHub Actions ci.yml (PR 빌드+helm lint) / cd.yml (matrix GHCR push + Helm upgrade)
- [x] kind 로컬 K8s 배포 검증: 14 Deployment + 3 HPA + Ingress 생성, 파드 HTTP 200 확인
  - ⚠️ GHCR 이미지 미push (main push 시 CD 자동 실행)

### Phase 9 — 앱 ✅ 완료
- [x] React Native(Expo) 프로젝트 (`mobile/` 디렉토리, Expo SDK 52, Expo Router v4)
- [x] 핵심 화면 6종: 시세판·인사이트·모의투자·커뮤니티·워치리스트·알림설정
- [x] STOMP WebSocket 시세·주문·채팅 실시간 연동
- [x] FCM 푸시 수신·딥링크 (`stockpulse://stock/{symbol}`)
- [x] EAS Build 설정 (development/preview/production 3개 프로파일)
  - ⚠️ Firebase 에셋(google-services.json/GoogleService-Info.plist) 수동 배치 필요

---

## 3. 의존 관계 / 순서

- **Phase 0**은 모든 것의 선행(인프라·Gateway·Auth 없으면 진행 불가).
- **1 → 2 → 3**은 순차(시세 없으면 인사이트 불가, 인사이트 루프 위에 모의투자).
- **4**는 1~3 일부 완료 후 병행 가능(채팅은 Realtime Gateway[P1] 재사용).
- **5·6**은 1~4 기능 위에 횡단으로 적용(기능이 있어야 안정성·관측 의미 있음).
- **7**은 5·6 이후(안정성·관측 갖춘 상태에서 측정).
- **8**은 마지막 통합 배포, **9**(앱)는 8의 백엔드가 배포된 뒤.

---

## 4. 진행 규칙

- 각 작업 완료 시 todo.md `[x]` + 즉시 커밋·push.
- Phase 착수 시 해당 Phase의 상세 구현 플랜을 별도로 작성.
- 외부 API 의존 코드는 **작성 전 실제 접근 검증** 먼저.
