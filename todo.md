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
- [x] Phase 8 — 배포/가용성 (K8s+HPA, Helm+GitHub Actions, 웹 실배포)
  - [x] Helm 차트 (단일 차트 + services range, 인프라 subchart dependency)
  - [x] 14개 서비스 Dockerfile (멀티스테이지 gradle:8.10-jdk21 → temurin:21-jre-alpine)
  - [x] GitHub Actions ci.yml (PR 빌드+테스트+helm lint) / cd.yml (matrix 14 이미지 GHCR push + Helm deploy)
  - [x] K8s 배포 검증 (kind 로컬): Helm install 성공, 14 Deployment + 3 HPA + Ingress 생성, 파드 HTTP 200 확인
    - ⚠️ app 파드: GHCR 이미지 없음 → CD 파이프라인(main push) 실행 시 자동 push·Running 전환
    - ⚠️ 인프라 subchart: Bitnami 2024-11 Docker Hub 중단으로 kind dev에선 비활성 (docker-compose 유지), prod values에서 활성화
  - [x] HPA 스케일아웃 확인: 3 HPA(api-gateway·market-collector·trading, CPU 70%, min1/max3) K8s 오브젝트 배포 완료
    - ⚠️ 실 트리거 테스트: 앱 이미지 push 후 k6 부하 재실행 필요
- [x] Phase 9 — 앱 (React Native/Expo, FCM 푸시)
  - [x] React Native(Expo) 프로젝트 초기 구성 (mobile/ 디렉토리, TypeScript strict, Expo Router v4 탭+스택)
  - [x] 핵심 화면 6종 (시세판·인사이트·모의투자·커뮤니티·워치리스트·알림설정)
  - [x] API 연동 (Axios+React Query v5, STOMP WebSocket 시세·주문·채팅)
  - [x] FCM 푸시 수신·딥링크 (expo-notifications, 포그라운드/백그라운드 핸들러)
  - [x] Expo 빌드·배포 설정 (app.json, eas.json development/preview/production)
  - ⚠️ 미배치 항목: Firebase 에셋(google-services.json/GoogleService-Info.plist) — Firebase 콘솔 앱 등록 후 배치 필요

---

## 🔴 잔여 실행 작업 (우선순위 순)

> 코드 작업 Phase 0~9 전부 완료. 아래를 순서대로 진행한다.

### A. GHCR 이미지 push + K8s 파드 Running ⭐ 최우선
> 완료 시: kind 클러스터 앱 파드 전체 Running 전환

- [ ] A-1. cd.yml `deploy` job에 `KUBECONFIG` 미설정 시 skip 조건 추가 (이미지만 push해도 OK)
- [ ] A-2. `git push origin main` → CD workflow 트리거 → GHCR 14개 이미지 push (~30분)
- [ ] A-3. kind에 GHCR pull secret 등록: `kubectl create secret docker-registry ghcr-pull-secret -n stockpulse-app --docker-server=ghcr.io --docker-username=hhho0coco1-star --docker-password=<PAT>`
- [ ] A-4. `kubectl rollout restart deployment -n stockpulse-app` → 새 이미지 적용
- [ ] A-5. `kubectl get pods -n stockpulse-app` → 14개 전부 Running 확인

### B. mobile/.env 생성 (5분)
> 완료 시: Expo 앱이 실 서버에 연결

- [x] B-1. `mobile/.env` 로컬 개발용 생성 (`API_BASE_URL=http://localhost:8080/api/v1`)
- [ ] B-2. `mobile/.env.production` 실 서버 주소로 생성 (A 완료 후)

### C. Firebase 앱 등록 + FCM 에셋 (수동 ~20분)
> 완료 시: FCM 푸시 알림 동작

- [ ] C-1. [Firebase 콘솔](https://console.firebase.google.com) → 프로젝트 생성 → Android 앱 등록 (패키지: `com.stockpulse.app`)
- [ ] C-2. iOS 앱 등록 (번들ID: `com.stockpulse.app`)
- [ ] C-3. `google-services.json` → `mobile/` 복사
- [ ] C-4. `GoogleService-Info.plist` → `mobile/` 복사

### D. k6 부하 테스트 실행 + 결과 기입 (A 완료 후)
> 완료 시: docs/10·11 실측값 채워짐

- [ ] D-1. `docker-compose up -d` (인프라 기동 확인)
- [ ] D-2. `k6 run --env K6_BASE_URL=http://localhost:8080 tests/load/scenarioA_quote.js`
- [ ] D-3. `k6 run --env K6_BASE_URL=http://localhost:8080 tests/load/scenarioB_order.js`
- [ ] D-4. `k6 run --env K6_BASE_URL=http://localhost:8080 tests/load/all.js`
- [ ] D-5. docs/10_부하테스트_결과.md 실측값(RPS·p95·에러율) 기입
- [ ] D-6. docs/11_장애테스트_결과.md Chaos Mesh 시나리오 결과 기입

### E. HPA 실트리거 확인 (A·D 완료 후)
> 완료 시: K8s 스케일아웃 동작 증명

- [ ] E-1. `kubectl get hpa -n stockpulse-app -w` (모니터링)
- [ ] E-2. k6 고부하 실행 → api-gateway·trading-service 레플리카 1→3 증가 확인
- [ ] E-3. 부하 제거 후 레플리카 1로 복귀 확인

### F. EAS Build (B·C 완료 후)
> 완료 시: 실제 APK 생성

- [ ] F-1. `cd mobile && npm install`
- [ ] F-2. `npx eas login` (Expo 계정)
- [ ] F-3. `npx eas build --platform android --profile preview`

### G. README 업데이트 (선택)
- [ ] G-1. 아키텍처 다이어그램 이미지 추가
- [ ] G-2. 실행 방법 (docker-compose / kind K8s) 기술
- [ ] G-3. 앱·화면 스크린샷 추가

### H. GKE Autopilot 실배포 (선택)
- [ ] H-1. GKE Autopilot 클러스터 생성
- [ ] H-2. `KUBECONFIG` GitHub 시크릿 등록 (`kubectl config view --raw | base64`)
- [ ] H-3. GitHub Secrets 등록: `POSTGRES_PASSWORD` `MONGO_PASSWORD` `REDIS_PASSWORD` `JWT_SECRET` `KIS_APP_KEY` `KIS_APP_SECRET` `NAVER_CLIENT_ID` `NAVER_CLIENT_SECRET` `DART_API_KEY`
- [ ] H-4. `git push origin main` → CD 재실행 → GKE 배포 확인

### I. React 웹 클라이언트 (선택, 미착수)
- [ ] I-1. React 18 + Vite + Tailwind + TradingView 차트 프로젝트 초기 구성 (`web/` 디렉토리)
- [ ] I-2. 시세판·인사이트·모의투자·커뮤니티 화면 구현
