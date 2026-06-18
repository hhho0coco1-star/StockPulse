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

### Phase 0 — 기반
- [ ] 모노레포 디렉토리 구조 + Gradle 멀티모듈(또는 서비스별 디렉토리) 세팅
- [ ] `docker-compose.yml`: Kafka(KRaft)·Redis·PostgreSQL·TimescaleDB·MongoDB
- [ ] 외부 API 키 발급(KIS·네이버·DART) — **사용자**
- [ ] 키로 실제 1회 호출 검증(KIS WS 연결 1건, 네이버/DART REST 1건)
- [ ] API Gateway(Spring Cloud Gateway): JWT 필터·라우팅·Redis Rate Limit
- [ ] Auth/User: 회원가입·로그인·JWT 발급·OAuth2 골격
- [ ] 공통 모듈: 응답/에러 포맷, Outbox 공통 구현 베이스
- [ ] README 골격 + CI 최소 워크플로(빌드)

### Phase 1 — 시세 파이프라인 (수직 슬라이스 시작)
- [ ] Market Collector: KIS WS 수신(Coroutines) → `market.tick` 발행
- [ ] 시세 리플레이/증폭 모드(부하·장 마감 시간용)
- [ ] TimescaleDB 적재 + 캔들 연속 집계
- [ ] Redis 시세 캐시(`quote:{symbol}`)
- [ ] Realtime Gateway: `/topic/market/{symbol}` WS 푸시 + Redis Pub/Sub
- [ ] Market REST: 종목 검색·현재가·캔들 조회
- [ ] 프론트 시세판(React + TradingView 차트)

### Phase 2 — 인사이트 엔진
- [ ] News Collector: 네이버 뉴스 수집 → `news.raw`
- [ ] Fundamentals Collector: DART 재무 수집 → `fundamentals.raw`
- [ ] Insight Service: Kafka Streams 윈도우 집계(모멘텀) + 실적·감성 종합
- [ ] 감성 분석(룰/LLM — 착수 시 확정)
- [ ] 전망 카드 MongoDB 저장 + `insight.updated` 발행
- [ ] Insight REST(카드·근거) + Realtime 인사이트 푸시
- [ ] 프론트 전망 카드 UI

### Phase 3 — 모의투자 Saga (수직 슬라이스 완성)
- [ ] Account: 잔고·원장·Outbox
- [ ] Portfolio: 보유종목·평가·Outbox
- [ ] Trading: 주문 접수·Saga 오케스트레이터·order_saga 상태
- [ ] Saga 흐름: 예약→체결→보유갱신→확정 + 단계별 보상
- [ ] 멱등성(processed_event) 처리
- [ ] Ranking: Redis Sorted Set 수익률 랭킹
- [ ] 프론트 주문·보유·수익률·랭킹 UI

### Phase 4 — 커뮤니티·실용 기능
- [ ] Community: 게시글·댓글·좋아요 + Outbox
- [ ] Realtime 채팅: `/app/chat/{symbol}` → `/topic/chat/{symbol}` fan-out
- [ ] 워치리스트(Auth/User)
- [ ] Notification/Alert: 알림 규칙·디바이스 토큰·이력
- [ ] FCM 연동 + 규칙 평가(목표가·급등락·뉴스)
- [ ] 프론트 커뮤니티·알림 설정 UI

### Phase 5 — 안정성
- [ ] 외부 API 전부 Resilience4j(CB·retry·timeout·bulkhead)
- [ ] Kafka/Redis 다운 시나리오 대응(폴백·재시도)
- [ ] Saga 보상 실패·교착 처리(타임아웃·DLQ)
- [ ] Outbox 발행기 견고화

### Phase 6 — 관측
- [ ] OpenTelemetry 계측 → Tempo(트레이스)
- [ ] Prometheus 메트릭 + Grafana 대시보드
- [ ] Loki 로그 수집
- [ ] 핵심 알람(SLO) 설정

### Phase 7 — 부하/장애 테스트
- [ ] k6 시나리오(로그인·시세·주문·WS 동시접속)
- [ ] 시세 증폭 부하로 처리량·지연 측정
- [ ] Chaos Mesh 장애 주입(Pod kill·네트워크 지연)
- [ ] 결과·병목·개선 문서화(10·11·12 문서)

### Phase 8 — 배포/가용성
- [ ] Helm 차트(서비스별)
- [ ] GitHub Actions: 빌드→Testcontainers 테스트→이미지→배포
- [ ] K8s 배포(3 namespace) + HPA
- [ ] 웹 실배포 + 면책·온보딩

### Phase 9 — 앱
- [ ] React Native(Expo) 프로젝트 + 시세·인사이트·커뮤니티 화면
- [ ] FCM 푸시 수신·딥링크
- [ ] Expo 빌드·배포

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
