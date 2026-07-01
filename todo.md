# StockPulse 작업 추적 (todo)

> 완료 시 `[x]` 체크. 큰 묶음이 끝나면 정리한다. 상세 로드맵은 docs/01_기획서.md 참고.

## 핵심 결정 메모 (확정)

- **프로젝트 목적: 학습/포트폴리오 (실사용자 목표는 보류, 2026-06-19 결정)** — 헤비 설계 그대로 완성·연습에 집중. SafeAlert가 이미 분산시스템 포트폴리오 커버.
- 도메인: 국내+해외 주식 (시세=KIS, 뉴스=네이버, 재무=DART)
- 스택: Kotlin+Spring Boot MSA, +Kafka Streams·TimescaleDB·Coroutines·Testcontainers·CI/CD(Helm+GitHub Actions)·TradingView, 관측 Loki+Tempo, 후반 Chaos Mesh
- 클라이언트: React 웹 + React Native(Expo) 앱 + FCM
- 서비스 13개 (Account/Portfolio 분리 → Saga 3개 서비스 분산 트랜잭션)
- 실용 기능: 워치리스트 + FCM 알림. 실거래는 자본시장법상 불가 → 가상 유지
- **모의투자 포지셔닝**: "초보 투자자를 위한 인사이트 실습 도구" — 인사이트 보고→모의 매매→결과 학습 루프. 단독 샌드박스 아님

---

## ✅ 완료: 설계 문서 (01~08, 코딩 전)

- [x] 01_기획서.md
- [x] 02_시스템아키텍처.md — 구성도·서비스목록(13개)·토픽·데이터흐름·확장성·배포토폴로지
- [x] 03_API설계.md — 공통규약·서비스별 REST·WebSocket 채널·FCM 페이로드
- [x] 04_DB설계.md — 저장소별 역할·PG 테이블(서비스별+Outbox)·TimescaleDB·Mongo·Redis 키
- [x] 05_WBS_개발계획.md — 마일스톤(DoD)·Phase별 작업분해·의존관계·진행규칙
- [x] 06_와이어프레임.md (+ HTML 프로토타입 3화면) — 전 7화면 설계·웹/앱 차이
- [x] 07_기술선택이유.md — 아키텍처·추가스택(6+2)·기존스택·제외기술 근거
- [x] 08_시퀀스다이어그램.md — 로그인·시세푸시·모의투자Saga(보상)·인사이트4축+백테스트

## 골격만 (개발 진행하며 채움)

- [x] 09_개발환경.md (골격)
- [x] 10_부하테스트_결과.md (골격)
- [x] 11_장애테스트_결과.md (골격)
- [x] 12_트러블슈팅_기록.md (골격)

---

## ▶ Phase 0 — 기반 구축 (현재 단계)

- [x] git init + 원격 연결 + .gitignore (StockPulse repo)
- [x] 0-1 Gradle 멀티모듈 루트(settings/build.gradle.kts) + common 모듈 골격
- [x] 0-2 docker-compose.yml (Kafka·Redis·PostgreSQL·TimescaleDB·MongoDB)
- [x] 0-3 외부 API 키 3종 발급 (KIS·네이버·DART) — **사용자**
- [x] 0-4 발급 키로 실제 1회 호출 검증
- [x] 0-5 api-gateway + auth-service Kotlin 골격 (services/)
- [x] 0-6 README 골격

## Phase 1~8 (요약 — 시작 시 상세화)

- [x] Phase 1 — 시세 파이프라인 (Collector→Kafka→Redis→WebSocket, 프론트 시세판)
  - ✅ D6·D8 검증 완료 (2026-06-30) — WebSocket URL: ws://ops.koreainvestment.com:31000, 삼성전자 실시간 틱 수신 + TimescaleDB 적재 확인
- [x] Phase 2 — 인사이트 엔진 (News/Fundamentals Collector + Insight Service)
  - ⚠️ D3(fundamentals-collector) 런타임 검증 별도 필요 — DART 재무 수집은 cron(매일 02:00) 기반, 첫 수집 후 /insights 점수 정상화 예상
- [x] Phase 3 — 모의투자 Saga (Trading·Account·Portfolio·Ranking, Outbox+보상)
- [x] Phase 4 — 커뮤니티·실용기능 (토론방·실시간 채팅·게시글, 워치리스트, 알림규칙+FCM)
- [x] Phase 5 — 안정성 (CB/retry/timeout, Kafka·Redis 장애 대응)
  - Resilience4j(CB·Retry·TimeLimiter), Kafka DLT, Redis fallback, GlobalExceptionHandler(6개 서비스) + 요청 검증 표준화
- [x] Phase 6 — 관측 (Prometheus·Grafana·Loki·Tempo)
  - 14개 서비스 Micrometer 계측(prometheus·tracing-otel·loki appender) + reactive 3개 Reactor Context 전파 + 관측 인프라 5컨테이너. OTLP endpoint는 HTTP 4318/v1/traces(스펙 4317 gRPC 오류 수정). 검증: 메트릭 노출·타겟 UP·Loki 로그·Tempo 트레이스 PASS
- [x] Phase 7 — 부하/장애 테스트 (k6, Chaos Mesh 장애 주입, 트러블슈팅)
  - k6 스크립트 4종(scenarioA·B·C·all) + Chaos Mesh YAML 3종(pod-kill·network-delay·kafka-offline) + docs/10·11·12 갱신. Prometheus 포트 버그(notification-service 8093 누락) 수정. 실부하 미실행(Phase 8 K8s 배포 후 재측정 예정).
- [ ] Phase 8 — 배포/가용성 (K8s+HPA, Helm+GitHub Actions, 웹 실배포)
- [ ] Phase 9 — 앱 (React Native/Expo, FCM 푸시)
