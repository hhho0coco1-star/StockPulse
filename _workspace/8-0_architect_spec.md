# Phase 8 — 배포/가용성 아키텍트 스펙 (8-0)

> 목표: Helm 차트 + GitHub Actions CI/CD + K8s HPA + 웹 실배포. Phase 1~7 완료 코드를 컨테이너화해 K8s 클러스터에 무중단 배포 가능한 상태로 만든다.
> 대상 리포: `c:\study\StockPulse` — Kotlin+Spring Boot MSA, Gradle 멀티모듈, 서비스 14개.
> 작성일 기준으로 `services/` 실제 모듈과 `application.yml` 포트를 대조해 확정했다.

---

## 0. 사실 확인 (코드 대조 결과) — 스펙의 근거

문서(`02_시스템아키텍처.md`)는 개념상 13개 서비스로 서술하나, **실제 Gradle 모듈(`settings.gradle.kts`)은 14개**다. 문서의 "Community"는 `discussion-service`로, "Auth/User"의 워치리스트 기능은 별도 `watchlist-service`로 분리 구현되었다. 아래 표가 **배포 대상의 단일 진실(source of truth)** 이며, 포트는 각 서비스 `src/main/resources/application.yml`의 `server.port` 실측값이다.

| # | Gradle 모듈 | 서비스명(image/deploy) | 포트 | 유형 | 외부노출 | DB/인프라 의존 | HPA |
|---|-------------|------------------------|------|------|----------|----------------|-----|
| 1 | `services:api-gateway` | api-gateway | 8080 | Spring Cloud Gateway (WebFlux) | **Ingress** | Redis(rate limit) | ✅ |
| 2 | `services:auth-service` | auth-service | 8081 | Spring MVC | 내부 | PostgreSQL, Redis | |
| 3 | `services:market-collector` | market-collector | 8082 | Coroutines+WS | 내부 | Kafka, TimescaleDB, Redis, KIS API | ✅ |
| 4 | `services:news-collector` | news-collector | 8083 | Coroutines | 내부 | Kafka, 네이버 API | |
| 5 | `services:fundamentals-collector` | fundamentals-collector | 8084 | Coroutines | 내부 | Kafka, DART API | |
| 6 | `services:insight-service` | insight-service | 8085 | Kafka Streams | 내부 | Kafka, MongoDB, TimescaleDB | (선택) |
| 7 | `services:trading-service` | trading-service | 8086 | Saga+Outbox | 내부 | PostgreSQL, Kafka | ✅ |
| 8 | `services:account-service` | account-service | 8087 | Outbox | 내부 | PostgreSQL, Kafka | |
| 9 | `services:portfolio-service` | portfolio-service | 8088 | Outbox | 내부 | PostgreSQL, Kafka | |
| 10 | `services:ranking-service` | ranking-service | 8089 | Redis ZSet | 내부 | Redis, Kafka | |
| 11 | `services:realtime-gateway` | realtime-gateway | 8090 | WebSocket(STOMP) | **Ingress** `/ws` | Redis Pub/Sub, Kafka | (선택) |
| 12 | `services:discussion-service` | discussion-service | 8091 | Spring MVC+Outbox | 내부 | PostgreSQL, Redis, Kafka | |
| 13 | `services:watchlist-service` | watchlist-service | 8092 | Spring MVC | 내부 | PostgreSQL | |
| 14 | `services:notification-service` | notification-service | 8093 | Kafka+FCM | 내부 | Kafka, PostgreSQL, FCM | |

> ⚠️ 문서(`02` 3장)의 포트 표는 Notification=8092, Community=8091로 되어 있으나 **실제 코드는 discussion=8091 / watchlist=8092 / notification=8093**이다. 배포는 코드 실측값을 따른다. 구현 후 `02_시스템아키텍처.md` 포트 표 동기화 필요(별도 문서 작업).

**공통 전제(모든 서비스 공통):**
- Spring Boot Actuator 노출: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`. (Phase 6 관측 계층에서 이미 actuator + micrometer 활성.)
- 빌드 산출물: `bootJar` → `services/{name}/build/libs/{name}-0.0.1-SNAPSHOT.jar` (root version `0.0.1-SNAPSHOT`).
- JDK: 빌드 toolchain **JDK 21**, 런타임 이미지 **temurin 21-jre**.

---

## §1 — Helm 차트 구조

### 1.1 방침: 단일 차트 + values 분기 (subchart 아님)

14개 서비스는 Deployment/Service/HPA/Ingress 형태가 거의 동일하므로 **서비스별 subchart를 만들지 않는다.** 대신 **하나의 `stockpulse` 애플리케이션 차트**에 공통 템플릿을 두고, `values.yaml`의 `services:` 맵으로 서비스별 차이(포트·replicas·env·resources·HPA on/off)만 분기한다. 인프라(Kafka/Redis/PG/TS/Mongo)는 Bitnami/timescale 차트를 **의존성(dependencies)** 으로 끌어온다(§2).

근거: 14개 subchart는 중복 90%에 유지비만 폭증. 단일 차트 + range 루프가 학습/포트폴리오 규모에 최적이고 `helm template` 검증도 단순하다.

### 1.2 디렉토리 구조

```
helm/
└── stockpulse/
    ├── Chart.yaml                 # umbrella: 앱 차트 + 인프라 subchart 의존성 선언
    ├── values.yaml               # 기본값 (dev). services 맵 + infra 토글
    ├── values-dev.yaml           # 로컬/kind/minikube 오버라이드 (replicas=1, PVC 작게)
    ├── values-prod.yaml          # 클라우드 오버라이드 (resources 상향, secret 외부참조)
    ├── charts/                    # helm dependency build 결과 (Bitnami 등 tgz 캐시)
    ├── templates/
    │   ├── _helpers.tpl          # 라벨/이름/이미지태그 헬퍼
    │   ├── configmap.yaml        # 공통 비민감 env (SPRING_PROFILES_ACTIVE, 서비스 DNS 등)
    │   ├── deployment.yaml       # range .Values.services → 서비스별 Deployment 생성
    │   ├── service.yaml          # range → ClusterIP Service 생성
    │   ├── hpa.yaml              # range (hpa.enabled=true 인 서비스만)
    │   ├── ingress.yaml         # api-gateway + realtime-gateway 라우팅
    │   ├── secret-ref.yaml      # (선택) External/placeholder — 실제 값은 kubectl로 주입
    │   └── NOTES.txt
    └── .helmignore
```

### 1.3 `values.yaml` 핵심 스키마

```yaml
global:
  imageRegistry: ghcr.io/hhho0coco0/stockpulse   # GHCR 네임스페이스
  imageTag: latest                                # cd.yml이 git sha로 override
  imagePullSecrets:
    - name: ghcr-pull-secret
  namespace: stockpulse-app

# 공통 기본값 (서비스가 개별 override 안 하면 이 값 사용)
defaults:
  replicas: 1
  resources:
    requests: { cpu: 100m, memory: 384Mi }
    limits:   { cpu: 500m, memory: 768Mi }
  probes:
    liveness:  { path: /actuator/health/liveness,  initialDelaySeconds: 40, periodSeconds: 10, failureThreshold: 3 }
    readiness: { path: /actuator/health/readiness, initialDelaySeconds: 20, periodSeconds: 5,  failureThreshold: 6 }
  hpa:
    enabled: false
    minReplicas: 1
    maxReplicas: 3
    cpuUtilization: 70

# 서비스별 정의 (name = 모듈명, deployment/image/service 이름 그대로 사용)
services:
  api-gateway:
    port: 8080
    hpa: { enabled: true }
    resources: { requests: { cpu: 200m, memory: 512Mi }, limits: { cpu: "1", memory: 1Gi } }
    envFromConfig: [SPRING_PROFILES_ACTIVE, REDIS_HOST, REDIS_PORT]
    envFromSecret: [REDIS_PASSWORD, JWT_SECRET]
  auth-service:
    port: 8081
    envFromSecret: [POSTGRES_PASSWORD, JWT_SECRET, REDIS_PASSWORD]
  market-collector:
    port: 8082
    hpa: { enabled: true }
    envFromSecret: [KIS_APP_KEY, KIS_APP_SECRET, REDIS_PASSWORD, TIMESCALE_PASSWORD]
  news-collector:
    port: 8083
    envFromSecret: [NAVER_CLIENT_ID, NAVER_CLIENT_SECRET]
  fundamentals-collector:
    port: 8084
    envFromSecret: [DART_API_KEY]
  insight-service:
    port: 8085
    envFromSecret: [MONGO_PASSWORD, TIMESCALE_PASSWORD]
  trading-service:
    port: 8086
    hpa: { enabled: true }
    envFromSecret: [POSTGRES_PASSWORD]
  account-service:      { port: 8087, envFromSecret: [POSTGRES_PASSWORD] }
  portfolio-service:    { port: 8088, envFromSecret: [POSTGRES_PASSWORD] }
  ranking-service:      { port: 8089, envFromSecret: [REDIS_PASSWORD] }
  realtime-gateway:     { port: 8090, envFromSecret: [REDIS_PASSWORD, JWT_SECRET] }
  discussion-service:   { port: 8091, envFromSecret: [POSTGRES_PASSWORD, REDIS_PASSWORD] }
  watchlist-service:    { port: 8092, envFromSecret: [POSTGRES_PASSWORD] }
  notification-service: { port: 8093, envFromSecret: [POSTGRES_PASSWORD, FCM_CREDENTIALS] }

ingress:
  enabled: true
  className: nginx
  host: stockpulse.example.com   # prod: 실제 도메인 / dev: nip.io 또는 localhost

infra:              # §2 참조 — subchart 토글
  kafka:       { enabled: true }
  redis:       { enabled: true }
  postgresql:  { enabled: true }
  timescaledb: { enabled: true }
  mongodb:     { enabled: true }
```

### 1.4 Deployment 템플릿 요건 (`templates/deployment.yaml`)

`range $name, $svc := .Values.services` 로 14개 생성. 각 Deployment는:

- **metadata.name** = `$name`, **labels** = `_helpers.tpl`의 공통 라벨(app.kubernetes.io/name, instance, part-of=stockpulse) + `app: {name}`.
- **replicas**: `$svc.replicas | default .Values.defaults.replicas`. **HPA가 켜진 서비스는 `replicas` 필드를 렌더링하지 않는다**(HPA가 소유권을 갖도록 — spec.replicas와 HPA 충돌 방지).
- **image**: `{{ .Values.global.imageRegistry }}/{name}:{{ .Values.global.imageTag }}`.
- **imagePullSecrets**: `global.imagePullSecrets`.
- **containerPort**: `$svc.port`.
- **env**:
  - 공통 비민감값 → `envFrom: configMapRef: stockpulse-common-config` (SPRING_PROFILES_ACTIVE=prod, KAFKA_BOOTSTRAP_SERVERS, 각 인프라 서비스 DNS·포트, 다운스트림 서비스 URL).
  - 민감값 → `env` 항목별 `valueFrom.secretKeyRef` (secret 이름 `stockpulse-secrets`, key = `$svc.envFromSecret` 각 항목).
- **resources**: `$svc.resources | default .Values.defaults.resources` (request/limit 모두 명시 — HPA CPU% 계산에 request 필수).
- **livenessProbe / readinessProbe**: httpGet, port=`$svc.port`, path=defaults.probes.* (actuator liveness/readiness). Spring Boot는 `management.endpoint.health.probes.enabled=true` + `management.endpoints.web.exposure.include=health,prometheus` 필요 → ConfigMap로 주입.
- **startupProbe**(권장): 첫 기동 지연(JVM+Spring context) 흡수. httpGet readiness, failureThreshold=30, periodSeconds=5 → 최대 150s 기동 허용 후 liveness 시작.

### 1.5 Service 템플릿 (`templates/service.yaml`)

- 전 서비스 **ClusterIP** (`range`로 14개). name=`$name`, port=`$svc.port`→targetPort=`$svc.port`.
- 외부 노출은 Service 타입이 아닌 **Ingress**로 일원화한다(api-gateway/realtime-gateway도 ClusterIP). LoadBalancer/NodePort는 사용하지 않음 — Ingress Controller 하나가 단일 외부 엔드포인트.
  - (대안: nginx ingress controller가 없는 순수 로컬이면 `api-gateway`만 NodePort로 두는 dev 오버라이드 허용. `values-dev.yaml`에서 토글.)

### 1.6 HPA 템플릿 (`templates/hpa.yaml`)

`range`로 순회하되 `$svc.hpa.enabled == true` 인 서비스만 렌더. 대상: **api-gateway · market-collector · trading-service** (스펙 요구). insight/realtime는 선택(문서 7장은 확장 대상이나 Kafka Streams/WS 세션은 CPU 기준 HPA와 궁합이 낮아 기본 off, 필요 시 values로 on).

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: {name}-hpa }
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: {name} }
  minReplicas: 1
  maxReplicas: 3
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
```

> HPA CPU 기준이 동작하려면 클러스터에 **metrics-server**가 설치돼 있어야 한다(GKE/NKS 기본 제공, kind/minikube는 별도 설치 — DoD 7 전제).

### 1.7 Ingress 템플릿 (`templates/ingress.yaml`)

nginx ingress class. `03_API설계.md` 계약 기준:

| path | pathType | backend | 비고 |
|------|----------|---------|------|
| `/api/v1/` | Prefix | `api-gateway:8080` | 모든 REST. Gateway가 JWT 검증 후 다운스트림 라우팅 |
| `/ws` | Prefix | `realtime-gateway:8090` | STOMP/SockJS. WebSocket 업그레이드 |

WebSocket용 어노테이션 필수:
```yaml
annotations:
  nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
  nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
  nginx.ingress.kubernetes.io/rewrite-target: /
```
> `api-gateway`의 라우팅 설정(`application.yml`의 spring.cloud.gateway.routes)이 `/api/v1/**`를 각 서비스로 분배하므로, Ingress는 `/api/v1/`을 통째로 gateway에 넘기기만 하면 된다(경로 재작성 없이). realtime은 `/ws`를 realtime-gateway로.

### 1.8 Secret 처리 (git 제외 원칙 준수 — MEMORY 비밀값 원칙)

- **차트에는 Secret 값을 넣지 않는다.** `templates/`에 `Secret` manifest를 커밋하지 않고, 배포 시 `kubectl create secret generic stockpulse-secrets --from-env-file=.env` 로 클러스터에 주입한다.
- 키 목록: `POSTGRES_PASSWORD`, `TIMESCALE_PASSWORD`, `MONGO_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `KIS_APP_KEY`, `KIS_APP_SECRET`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `DART_API_KEY`, `FCM_CREDENTIALS`(서비스계정 JSON — file mount 방식 권장, §1.9).
- `.env`는 계속 로컬/CI Secret에만 존재. `.gitignore`에 `.env`, `helm/**/secrets*.yaml` 추가 확인.
- CI/CD 경로: cd.yml이 GitHub Secrets → `kubectl create secret ... --dry-run=client -o yaml | kubectl apply -f -` (idempotent upsert).

### 1.9 FCM 자격증명 (특이 케이스)

FCM 서비스계정 JSON은 env 문자열보다 **파일 마운트**가 안전·자연스럽다:
- Secret `stockpulse-fcm` (key: `firebase-service-account.json`) → notification-service Deployment에 `volumeMount` (`/etc/fcm/`) + env `GOOGLE_APPLICATION_CREDENTIALS=/etc/fcm/firebase-service-account.json`.

---

## §2 — K8s 인프라 (Helm subchart 의존성)

로컬 `docker-compose.yml`의 5개 인프라를 K8s로 이관. `Chart.yaml`의 `dependencies`로 선언하고 `helm dependency build`로 `charts/`에 받는다. **네임스페이스 `stockpulse-infra`** (문서 8장 토폴로지)에 배포하되, 학습 규모에선 앱과 같은 릴리스로 묶어도 됨(values 토글).

### 2.1 `Chart.yaml` dependencies

```yaml
apiVersion: v2
name: stockpulse
version: 0.1.0
appVersion: "0.0.1"
dependencies:
  - name: kafka
    version: "~30.x"          # Bitnami. KRaft 기본
    repository: oci://registry-1.docker.io/bitnamicharts
    condition: infra.kafka.enabled
  - name: redis
    version: "~20.x"
    repository: oci://registry-1.docker.io/bitnamicharts
    condition: infra.redis.enabled
  - name: postgresql
    version: "~16.x"
    repository: oci://registry-1.docker.io/bitnamicharts
    condition: infra.postgresql.enabled
  - name: mongodb
    version: "~16.x"
    repository: oci://registry-1.docker.io/bitnamicharts
    condition: infra.mongodb.enabled
  - name: timescaledb-single
    version: "~0.33.x"
    repository: https://charts.timescale.com
    alias: timescaledb
    condition: infra.timescaledb.enabled
```
> 버전은 `~x`로 표기 — 구현 시 `helm search repo`/`helm show chart`로 당시 최신 안정 마이너 고정. Bitnami는 OCI 레지스트리 사용(2024+ 표준). 이미지 접근 제한 이슈 시 `values-dev.yaml`에서 `image.registry` override.

### 2.2 인프라별 values 요건 (dev = 단일 replica, 최소 리소스)

| 인프라 | 차트 | 모드 | replica | PVC | 앱에서 접근 DNS |
|--------|------|------|---------|-----|-----------------|
| Kafka | bitnami/kafka | **KRaft**, controller+broker 겸용 | 1 | 4Gi | `{release}-kafka:9092` |
| Redis | bitnami/redis | **standalone**(sentinel/cluster off) | 1 (master) | 1Gi | `{release}-redis-master:6379` |
| PostgreSQL | bitnami/postgresql | primary 단일 | 1 | 4Gi | `{release}-postgresql:5432` |
| TimescaleDB | timescale/timescaledb-single | single(replica 0) | 1 | 4Gi | `{release}-timescaledb:5432` |
| MongoDB | bitnami/mongodb | standalone | 1 | 2Gi | `{release}-mongodb:27017` |

인프라 values 핵심(발췌):
```yaml
kafka:
  controller: { replicaCount: 1 }
  kraft: { enabled: true }
  listeners: { client: { protocol: PLAINTEXT } }   # 학습용. prod는 SASL 권장
  persistence: { size: 4Gi, storageClass: standard }
redis:
  architecture: standalone
  auth: { enabled: true, existingSecret: stockpulse-secrets, existingSecretPasswordKey: REDIS_PASSWORD }
  master: { persistence: { size: 1Gi, storageClass: standard } }
postgresql:
  auth: { existingSecret: stockpulse-secrets, secretKeys: { adminPasswordKey: POSTGRES_PASSWORD }, database: stockpulse }
  primary:
    persistence: { size: 4Gi, storageClass: standard }
    initdb:
      scriptsConfigMap: stockpulse-pg-init      # docker-compose init/postgres 의 6개 DB 생성 스크립트 이관
mongodb:
  architecture: standalone
  auth: { existingSecret: stockpulse-secrets }
  persistence: { size: 2Gi, storageClass: standard }
timescaledb:
  replicaCount: 1
  persistentVolumes: { data: { size: 4Gi, storageClass: standard } }
```

### 2.3 PVC / StorageClass

- 각 인프라 차트가 StatefulSet + `volumeClaimTemplates`로 PVC 자동 생성 → `storageClass: standard`.
  - GKE: `standard-rwo` (또는 기본 SC). NKS: `nks-block-storage`. kind: `standard`(rancher local-path). minikube: `standard`(hostpath) — **구현 시 클러스터의 실제 SC 이름으로 values override**.
- 앱 서비스(Deployment)는 **stateless → PVC 없음**.

### 2.4 초기화 스크립트 이관

`docker-compose.yml` L73/L95의 `init/postgres`, `init/timescaledb`를 K8s로:
- PG 6개 DB 생성 SQL → `ConfigMap stockpulse-pg-init` → bitnami postgresql `initdb.scriptsConfigMap`.
- TimescaleDB 확장 활성 SQL → timescaledb 차트 init hook 또는 별도 Job.

---

## §3 — Dockerfile (14개 서비스 공통 템플릿)

### 3.1 공통 멀티스테이지 패턴

각 `services/{name}/Dockerfile`. 빌드 컨텍스트는 **리포 루트**(Gradle 멀티모듈이라 `common` + `settings.gradle.kts` + 대상 모듈 필요). 서비스별로 다른 건 **`SERVICE` build-arg(모듈 경로)와 `EXPOSE` 포트뿐**.

```dockerfile
# ── build stage ────────────────────────────────────────────
FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
# 의존성 캐시 최적화: 빌드 스크립트/버전 카탈로그 먼저 복사
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle ./gradle
COPY common ./common
ARG SERVICE                      # 예: services/api-gateway
COPY ${SERVICE} ./${SERVICE}
# 대상 모듈만 bootJar (테스트 제외 — 테스트는 CI가 담당)
RUN gradle :$(echo ${SERVICE} | sed 's#/#:#g'):bootJar -x test --no-daemon

# ── runtime stage ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app     # 비루트 실행
ARG SERVICE
ARG PORT
COPY --from=build /workspace/${SERVICE}/build/libs/*-0.0.1-SNAPSHOT.jar app.jar
EXPOSE ${PORT}
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

> 요구 스펙의 `ENTRYPOINT ["java","-jar","app.jar"]`은 JAVA_OPTS 미적용 → 컨테이너 메모리 인지가 안 된다. **JAVA_OPTS wrapper 형태를 채택**(컨테이너 메모리 한계 준수, HPA/limit와 정합). 순수 형태가 필요하면 `ENTRYPOINT ["java","-jar","app.jar"]`로 축소 가능.

### 3.2 서비스별 차이 = 빌드 인자만

Dockerfile 본문은 14개 동일. build 시:
```
docker build -f services/api-gateway/Dockerfile \
  --build-arg SERVICE=services/api-gateway --build-arg PORT=8080 \
  -t ghcr.io/hhho0coco0/stockpulse/api-gateway:latest .
```
파일을 14벌 두되 각 파일 상단에 `ARG PORT` 기본값을 서비스 포트로 박아 두면(예: `ARG PORT=8080`) CI 매트릭스가 단순해진다. **동일 템플릿, 포트 기본값만 상이.**

### 3.3 이미지 크기/속도

- alpine JRE 기반 → 이미지 ~180MB.
- (선택 고도화) `-x test` 대신 CI에서 미리 만든 jar를 `COPY`만 하는 "thin Dockerfile"로 바꾸면 이미지 빌드가 초 단위. 단, 그러려면 CI가 jar를 아티팩트로 넘겨야 함 → §4에서 **CI 빌드 → jar 아티팩트 → Docker COPY** 경로를 권장 옵션으로 명시.
- `.dockerignore` 필수: `build/`, `.gradle/`, `**/build/`, `.git`, `_workspace`, `docs`, `*.md`.

---

## §4 — GitHub Actions CI/CD

### 4.1 `.github/workflows/ci.yml` (PR 검증)

트리거: `pull_request` → main.
```yaml
name: CI
on:
  pull_request: { branches: [main] }
jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Build & unit test (전체 모듈)
        run: ./gradlew build --no-daemon        # 컴파일 + 단위 테스트
      # Testcontainers 통합 테스트는 무거우므로 라벨/수동 트리거로 분리 권장
```
- DoD 4 충족: PR 열면 자동 실행, `./gradlew build` 성공.
- Testcontainers 테스트(문서 8장 CI 흐름)는 Docker-in-Docker 필요 → 별도 job(`if: contains(github.event.pull_request.labels.*.name, 'integration')`) 또는 nightly로 분리해 PR 피드백을 빠르게 유지.

### 4.2 `.github/workflows/cd.yml` (main merge → 배포)

트리거: `push` → main.
```yaml
name: CD
on:
  push: { branches: [main] }
permissions: { contents: read, packages: write }
jobs:
  images:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          [api-gateway, auth-service, market-collector, news-collector,
           fundamentals-collector, insight-service, trading-service, account-service,
           portfolio-service, ranking-service, realtime-gateway, discussion-service,
           watchlist-service, notification-service]
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GHCR_TOKEN }} }
      - uses: docker/build-push-action@v6
        with:
          context: .
          file: services/${{ matrix.service }}/Dockerfile
          build-args: |
            SERVICE=services/${{ matrix.service }}
          tags: |
            ghcr.io/hhho0coco0/stockpulse/${{ matrix.service }}:latest
            ghcr.io/hhho0coco0/stockpulse/${{ matrix.service }}:${{ github.sha }}
          push: true

  deploy:
    needs: images
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: azure/setup-helm@v4
      - name: Kubeconfig
        run: |
          mkdir -p ~/.kube
          echo "${{ secrets.KUBECONFIG }}" | base64 -d > ~/.kube/config
      - name: Upsert secrets (git 미포함 값 주입)
        run: |
          kubectl create secret generic stockpulse-secrets -n stockpulse-app \
            --from-literal=POSTGRES_PASSWORD='${{ secrets.POSTGRES_PASSWORD }}' \
            --from-literal=JWT_SECRET='${{ secrets.JWT_SECRET }}' \
            --from-literal=KIS_APP_KEY='${{ secrets.KIS_APP_KEY }}' \
            --from-literal=KIS_APP_SECRET='${{ secrets.KIS_APP_SECRET }}' \
            --from-literal=NAVER_CLIENT_ID='${{ secrets.NAVER_CLIENT_ID }}' \
            --from-literal=NAVER_CLIENT_SECRET='${{ secrets.NAVER_CLIENT_SECRET }}' \
            --from-literal=DART_API_KEY='${{ secrets.DART_API_KEY }}' \
            --from-literal=REDIS_PASSWORD='${{ secrets.REDIS_PASSWORD }}' \
            --dry-run=client -o yaml | kubectl apply -f -
      - name: Helm upgrade
        run: |
          helm dependency build helm/stockpulse
          helm upgrade --install stockpulse helm/stockpulse \
            -n stockpulse-app --create-namespace \
            -f helm/stockpulse/values-prod.yaml \
            --set global.imageTag=${{ github.sha }} \
            --wait --timeout 10m
```

### 4.3 필요한 GitHub Secrets

| Secret | 용도 |
|--------|------|
| `GHCR_TOKEN` | GHCR push (packages:write PAT 또는 GITHUB_TOKEN) |
| `KUBECONFIG` | 클러스터 접근(base64 인코딩된 kubeconfig) |
| `KIS_APP_KEY`, `KIS_APP_SECRET` | 한국투자증권 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 뉴스 |
| `DART_API_KEY` | DART |
| `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `MONGO_PASSWORD`, `TIMESCALE_PASSWORD` | 인프라·앱 |
| `FCM_CREDENTIALS` | Firebase 서비스계정 JSON(base64) |

> 이미지 태그는 `latest` + `sha` 두 벌 push, 배포는 `sha` 태그로 → 롤백 가능·이미지 캐시 무효화 정확.

---

## §5 — 배포 환경 선택 및 근거

### 5.1 결론: **2트랙 전략**

| 트랙 | 용도 | 도구 | 비용 |
|------|------|------|------|
| **개발/검증(기본)** | 매일 개발, CI 데모, Helm/HPA 검증 | **kind**(또는 minikube) 로컬 K8s | ₩0 |
| **포트폴리오 실배포(발표용)** | "클라우드 URL로 접속" 데모 | **GKE Autopilot** 또는 **NCloud NKS** | 아래 추정 |

근거: 14개 서비스 + 인프라 5종을 24/7 클라우드에 올리면 학습 프로젝트엔 과한 비용. **평소엔 kind로 무료 개발**, 발표/제출 기간에만 클라우드에 올렸다가 내리는 방식이 비용 대비 최적. DoD 5·6·7(모두 `kubectl`/HPA 기반)은 kind에서도 metrics-server만 깔면 전부 검증 가능.

### 5.2 클라우드 옵션 비교

**A. GKE Autopilot (권장 1순위)**
- 노드 관리 불필요, Pod 리소스 request 기준 과금 → 켜둔 만큼만.
- 신규 가입 $300 크레딧(90일) → 발표 기간 사실상 무료.
- 14서비스 request 합계 ≈ vCPU 2~3 / 메모리 6~8Gi. Autopilot 요금 ≈ 시간당 $0.15~0.25 → **월 상시 구동 시 $110~180**, 하지만 발표용으로 며칠만 켜면 **$5~15** 수준. metrics-server·nginx-ingress 기본/원클릭.
- 인프라(Kafka/PG 등)까지 클러스터 내부 구동 시 request가 늘어 비용 증가 → 발표 시에만.

**B. NCloud NKS (권장 2순위, 국내)**
- 국내 리전 지연 낮음(KIS/네이버 API와 같은 망). 학생/신규 크레딧 프로모션 활용 가능.
- 워커노드(예: 2vCPU 8GB) 1~2대 상시 구동 시 **월 8~15만원대** + LB/스토리지. 크레딧 소진 후 kind로 회귀.
- Control plane 무료, 워커 VM·블록스토리지·LB만 과금.

**C. 로컬 kind/minikube (상시 기본)**
- 클라우드 미사용. 단점: 외부 공인 IP 없음 → DoD 6의 "외부 IP 200" 데모는 `kubectl port-forward` 또는 `cloudflared`/`ngrok` 터널로 대체.

### 5.3 권장 운영 흐름
1. 평소: `kind create cluster` → `helm upgrade --install ... -f values-dev.yaml` → 개발·DoD 1~7 검증.
2. 발표 직전: GKE Autopilot 클러스터 생성 → cd.yml이 GHCR 이미지로 배포 → 공인 Ingress IP 확보 → 데모 → **끝나면 클러스터 삭제**(비용 정지).
3. 비용 상한: 클라우드는 발표 기간 한정, 예상 **$5~30 / 회차**.

---

## §6 — DoD (완료 기준) 및 검증 방법

| # | 완료 기준 | 검증 명령/방법 | 통과 조건 |
|---|-----------|----------------|-----------|
| 1 | Helm lint | `helm lint helm/stockpulse` | 에러 0 (warning 허용) |
| 2 | 템플릿 렌더 | `helm template helm/stockpulse -f helm/stockpulse/values-dev.yaml` | 14 Deployment+14 Service+3 HPA+Ingress+ConfigMap 렌더, YAML 오류 0 |
| 3 | 14 Dockerfile 빌드 | 각 서비스 `docker build -f services/{name}/Dockerfile --build-arg SERVICE=services/{name} .` | 14개 전부 성공, 이미지 생성 |
| 4 | CI 빌드 | PR 생성 → Actions `CI` | `./gradlew build` 성공(초록) |
| 5 | 배포 후 파드 상태 | `kubectl get pods -n stockpulse-app` | 14 앱 파드 + 인프라 파드 전부 `Running`/`Ready` |
| 6 | 외부 접근 | Ingress IP로 `GET /api/v1/health` (또는 api-gateway actuator) | HTTP 200 |
| 7 | HPA 스케일아웃 | trading-service에 CPU 부하(k6/`hey`) → `kubectl get hpa` `kubectl get pods` watch | replica가 1→2(3까지) 증가 확인 |

### 6.1 DoD 세부 주의
- **DoD 6**: `03_API설계.md`에 `/api/v1/health` 계약이 없으므로, 구현 시 **api-gateway에 `/api/v1/health` 헬스 라우트를 노출**하거나(권장), Ingress에 `/actuator/health` 경로를 추가로 매핑해 검증. 스펙상 후자보다 전자(게이트웨이 헬스 엔드포인트 추가)를 권장 → 실제 사용자 트래픽 경로와 동일하게 검증됨.
- **DoD 7 전제**: 클러스터에 metrics-server 설치, trading-service HPA enabled, resources.requests.cpu 명시(HPA % 계산 기준). 부하는 `/orders` 반복 호출 또는 CPU busy 엔드포인트.
- **DoD 5 전제**: `stockpulse-secrets`, `stockpulse-pg-init` ConfigMap 선주입. 인프라 파드가 먼저 Ready 되어야 앱 파드 readiness 통과(readinessProbe가 DB 연결 대기 흡수) → 앱 Deployment에 initContainer(nc 대기) 또는 넉넉한 startupProbe로 순서 의존성 완화.

---

## §7 — 구현 산출물 체크리스트 (backend-dev 인계용)

1. `helm/stockpulse/Chart.yaml` (+dependencies), `values.yaml`, `values-dev.yaml`, `values-prod.yaml`
2. `helm/stockpulse/templates/`: `_helpers.tpl`, `configmap.yaml`, `deployment.yaml`, `service.yaml`, `hpa.yaml`, `ingress.yaml`, `NOTES.txt`
3. `helm/stockpulse/pg-init/` → ConfigMap용 6-DB 생성 SQL(기존 `init/postgres`에서 이관)
4. `services/{14개}/Dockerfile` (공통 템플릿, PORT 기본값만 상이)
5. `.dockerignore` (루트)
6. `.github/workflows/ci.yml`, `.github/workflows/cd.yml`
7. `.gitignore` 갱신(`.env`, helm secret 산출물)
8. (문서 동기화) `02_시스템아키텍처.md` 포트 표를 코드 실측(discussion 8091/watchlist 8092/notification 8093)에 맞춰 정정
9. `README`에 배포 절차(kind 로컬 + 클라우드 발표) 섹션 추가

## §8 — 리스크 / 선결 확인
- **JVM 기동 시간**: 14 파드 동시 기동 시 CPU 경합 → startupProbe 넉넉히(150s). dev 노드 리소스 부족하면 kind 노드 spec 상향 필요.
- **Bitnami OCI 이미지 접근**: 최근 Bitnami 이미지 정책 변경 가능성 → 구현 착수 시 `helm show chart` 로 이미지/접근성 실검증(MEMORY: 코드 작성 전 외부 의존성 검증 원칙).
- **Kafka KRaft 단일 노드 PVC**: replica=1이라 파드 재기동 시 데이터 유지 위해 PVC 필수. storageClass 이름은 클러스터별 상이 → values로 반드시 override.
- **realtime-gateway 다중 인스턴스**: WS 세션은 인스턴스 분산되나 Redis Pub/Sub fan-out 전제(문서 7장). HPA on 시 sticky session 불필요(설계상 어느 인스턴스든 동일 메시지 수신).
```
