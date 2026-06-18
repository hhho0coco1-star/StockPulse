# StockPulse 작업 추적 (todo)

> 완료 시 `[x]` 체크. 큰 묶음이 끝나면 정리한다. 상세 로드맵은 docs/01_기획서.md 참고.

## 핵심 결정 메모 (확정)

- 도메인: 국내+해외 주식 (시세=KIS, 뉴스=네이버, 재무=DART)
- 스택: Kotlin+Spring Boot MSA, +Kafka Streams·TimescaleDB·Coroutines·Testcontainers·CI/CD(Helm+GitHub Actions)·TradingView, 관측 Loki+Tempo, 후반 Chaos Mesh
- 클라이언트: React 웹 + React Native(Expo) 앱 + FCM
- 서비스 13개 (Account/Portfolio 분리 → Saga 3개 서비스 분산 트랜잭션)
- 실용 기능: 워치리스트 + FCM 알림. 실거래는 자본시장법상 불가 → 가상 유지
- **모의투자 포지셔닝**: "초보 투자자를 위한 인사이트 실습 도구" — 인사이트 보고→모의 매매→결과 학습 루프. 단독 샌드박스 아님

---

## 진행 중: 설계 문서 작성 (코딩 전)

- [x] 01_기획서.md
- [x] 02_시스템아키텍처.md — 구성도·서비스목록(13개)·토픽·데이터흐름·확장성·배포토폴로지
- [x] 03_API설계.md — 공통규약·서비스별 REST·WebSocket 채널·FCM 페이로드
- [x] 04_DB설계.md — 저장소별 역할·PG 테이블(서비스별+Outbox)·TimescaleDB·Mongo·Redis 키
- [x] 05_WBS_개발계획.md — 마일스톤(DoD)·Phase별 작업분해·의존관계·진행규칙
- [x] 06_와이어프레임.md (+ HTML 프로토타입 3화면) — 전 7화면 설계·웹/앱 차이
- [x] 07_기술선택이유.md — 아키텍처·추가스택(6+2)·기존스택·제외기술 근거
- [ ] 08_시퀀스다이어그램.md — Saga·인사이트·시세푸시·채팅 플로우

## 골격만 (개발 진행하며 채움)

- [ ] 09_개발환경.md
- [ ] 10_부하테스트_결과.md
- [ ] 11_장애테스트_결과.md
- [ ] 12_트러블슈팅_기록.md

---

## Phase 0 — 기반 구축 (설계 문서 이후)

- [ ] 0-1 모노레포 폴더 구조 + git init + .gitignore
- [ ] 0-2 docker-compose.yml (Kafka·Redis·PostgreSQL·MongoDB)
- [ ] 0-3 외부 API 키 3종 발급 (KIS·네이버·DART) — **사용자**
- [ ] 0-4 발급 키로 실제 1회 호출 검증
- [ ] 0-5 API Gateway + Auth 서비스 Kotlin 골격
- [ ] 0-6 README 골격

## Phase 1~8 (요약 — 시작 시 상세화)

- [ ] Phase 1 — 시세 파이프라인 (Collector→Kafka→Redis→WebSocket, 프론트 시세판)
- [ ] Phase 2 — 인사이트 엔진 (News/Fundamentals Collector + Insight Service)
- [ ] Phase 3 — 모의투자 Saga (Trading·Account/Portfolio·Ranking, Outbox+보상)
- [ ] Phase 4 — 커뮤니티·실용기능 (토론방·실시간 채팅·게시글, 워치리스트, 알림규칙+FCM)
- [ ] Phase 5 — 안정성 (CB/retry/timeout, Kafka·Redis 장애 대응)
- [ ] Phase 6 — 관측 (Prometheus·Grafana·Loki·Tempo)
- [ ] Phase 7 — 부하/장애 테스트 (k6, Chaos Mesh 장애 주입, 트러블슈팅)
- [ ] Phase 8 — 배포/가용성 (K8s+HPA, Helm+GitHub Actions, 웹 실배포)
- [ ] Phase 9 — 앱 (React Native/Expo, FCM 푸시)
