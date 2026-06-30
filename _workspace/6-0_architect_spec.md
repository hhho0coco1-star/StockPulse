# Phase 6 관측(Observability) 구현 스펙

> 작성: architect / 대상: backend-dev (구현) · qa (검증)
> 범위: Prometheus(메트릭) + Grafana(대시보드) + Loki(로그) + Tempo(트레이스)
> 목표: 13개 서비스에 공통 적용할 **의존성·설정·인프라**를 표준화하고, 4스택이 실제로 동작(타겟 UP / 로그 수집 / 트레이스 연결)함을 확인.
> 원칙(docs/07 §2): "Grafana 하나로 메트릭+로그+트레이스 통합. ELK보다 경량·저비용." → 학습/포트폴리오 목적이므로 **단일 노드·개발용 100% 샘플링·과도한 무게 지양**.

---

## 0. 전제 · 현황 정리

### 0-1. 대상 서비스 (실제 디렉터리 기준 13개)
docs/02의 서비스명(Community/Notification)과 실제 디렉터리명이 다르다. **구현은 실제 디렉터리명을 기준**으로 한다.

| # | 서비스 디렉터리 | 포트 | 스택 종류 | 비고 |
|---|----------------|------|-----------|------|
| 1 | api-gateway | 8080 | **Reactive(WebFlux/Gateway)** | actuator 의존성 추가 필요 확인 |
| 2 | auth-service | 8081 | Servlet(Web) | |
| 3 | market-collector | 8082 | **Reactive(WebFlux)** | TimescaleDB(5433) |
| 4 | news-collector | 8083 | Servlet | Mongo |
| 5 | fundamentals-collector | 8084 | Servlet | |
| 6 | insight-service | 8085 | Servlet + Kafka Streams | Mongo |
| 7 | trading-service | 8086 | Servlet | Saga 오케스트레이터 |
| 8 | account-service | 8087 | Servlet | |
| 9 | portfolio-service | 8088 | Servlet | |
| 10 | ranking-service | 8089 | Servlet | Redis |
| 11 | realtime-gateway | 8090 | **Reactive(WebFlux/WS)** | |
| 12 | discussion-service | 8091 | Servlet + WS | (docs상 Community) |
| 13 | notification-service | 8092 | Servlet | (docs상 Notification/Alert) |

> ⚠️ **Servlet vs Reactive 구분 주의**: HTTP server 메트릭(`http.server.requests`)과 actuator 노출은 양쪽 공통이고 application.yml 표준 블록(§3)도 동일하게 적용 가능하다. 그러나 **트레이싱 컨텍스트(traceId)의 로그 전파는 reactive 서비스에 추가 설정이 필요**하다. WebFlux/코루틴은 ThreadLocal(MDC) 기반이 아니므로 traceId가 자동으로 MDC에 실리지 않아, 추가 설정 없이는 reactive 3개 서비스(api-gateway, market-collector, realtime-gateway)의 로그에 traceId가 비고 §5의 trace↔log 상관이 깨진다. → **§3-1 "Reactive 서비스 컨텍스트 전파" 참조.**

### 0-2. 이미 갖춰진 것
- `spring-boot-starter-actuator` 카탈로그에 선언됨(libs.versions.toml L53). 단, 모든 서비스 build.gradle.kts에 적용되어 있지는 않음 → **전 서비스 확인·추가 필요**.
- trading/market 등 일부 application.yml에 `management.endpoints.web.exposure.include: health,info,circuitbreakers,metrics` 존재. → **`prometheus` 추가 + tracing/OTLP 블록 추가 필요**.

### 0-3. 아직 없는 것 (Phase 6에서 추가)
- micrometer-registry-prometheus (Prometheus 노출 포맷)
- micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp (트레이스 OTLP 전송)
- loki-logback-appender (Loki 직접 전송) — 또는 Promtail로 컨테이너 로그 수집
- 관측 인프라 컨테이너 5종 (prometheus, grafana, loki, tempo, promtail)

---

## 1. 관측 아키텍처

### 1-1. 데이터 흐름

```
                    ┌──────────────────── 13 Spring Boot 서비스 ────────────────────┐
                    │  Micrometer(메트릭) · Micrometer Tracing(OTel bridge) · Logback │
                    └───────┬───────────────────┬────────────────────────┬──────────┘
                            │                    │                        │
              (pull) GET /actuator/prometheus    │ (push) OTLP/gRPC :4317 │ (push 또는 파일)
                            │                    │                        │
                            ▼                    ▼                        ▼
                   ┌──────────────┐      ┌──────────────┐        ┌──────────────────┐
                   │  Prometheus  │      │    Tempo     │        │ Loki             │
                   │   :9090      │      │ OTLP in:4317 │        │  :3100           │
                   │  (scrape)    │      │  query:3200  │        │ (logback appender│
                   └──────┬───────┘      └──────┬───────┘        │  또는 promtail)  │
                          │                     │                └────────┬─────────┘
                          │                     │                         │
                          └──────────┬──────────┴─────────────────────────┘
                                     ▼
                            ┌─────────────────┐
                            │     Grafana     │  datasource provisioning:
                            │      :3000      │  Prometheus / Loki / Tempo
                            │  (대시보드·탐색) │  + trace↔log 상관(traceId)
                            └─────────────────┘
```

**핵심 상관(correlation) 설계**
- 각 로그 라인에 `traceId`/`spanId`를 MDC로 박아 넣는다(Micrometer Tracing이 자동 주입).
- Grafana Tempo→Loki: traceId로 해당 트레이스의 로그를 점프(`tracesToLogsV2`).
- Grafana Loki→Tempo: 로그의 traceId에서 트레이스로 점프(derived field).
- Exemplar(메트릭→트레이스)는 선택. 학습 목적상 필수 아님.

### 1-2. 포트 매핑 (호스트 기준, 기존 인프라와 충돌 회피)

| 컴포넌트 | 호스트 포트 | 컨테이너 포트 | 프로토콜 | 용도 |
|----------|-------------|---------------|----------|------|
| Grafana | 3000 | 3000 | HTTP | UI |
| Prometheus | 9090 | 9090 | HTTP | UI / scrape API |
| Loki | 3100 | 3100 | HTTP | log push/query |
| Tempo (HTTP query) | 3200 | 3200 | HTTP | Grafana 조회 |
| Tempo (OTLP gRPC) | 4317 | 4317 | gRPC | 서비스→Tempo 트레이스 수신 |
| Tempo (OTLP HTTP) | 4318 | 4318 | HTTP | (대안) 트레이스 수신 |
| Promtail | (포트 없음) | - | - | 로그 파일/도커 수집 후 Loki push |

> 기존 docker-compose 점유 포트: 9092(kafka), 6379(redis), 5432(pg), 5433(timescale), 27017(mongo). **충돌 없음.**

### 1-3. 서비스→인프라 접속 방식 (로컬 개발 2가지 시나리오)

서비스는 호스트에서 `gradlew bootRun`으로, 인프라는 docker로 띄우는 혼합 환경이 기본이다. **접속 엔드포인트는 환경변수로 외부화**하여 두 시나리오 모두 지원한다.

| 변수 | 서비스가 호스트 실행 시 | 서비스도 docker 실행 시 |
|------|------------------------|--------------------------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` (Tempo) | `http://localhost:4317` | `http://tempo:4317` |
| `LOKI_URL` (logback appender) | `http://localhost:3100/loki/api/v1/push` | `http://loki:3100/loki/api/v1/push` |
| Prometheus가 서비스 scrape | `host.docker.internal:<port>` | `<service>:<port>` |

> **권장(학습 단순화)**: 서비스는 호스트 `bootRun`, 메트릭은 Prometheus가 `host.docker.internal`로 pull, 트레이스/로그는 서비스가 `localhost`로 push. 이 조합을 기본 시나리오로 문서화한다.

---

## 2. 의존성 추가 (gradle/libs.versions.toml)

### 2-1. [versions] 추가
```toml
# ── Phase 6 관측 ─────────────────────────────────────────────────────────────
micrometerTracing  = "1.3.5"   # Spring Boot 3.3.5 BOM 정렬 버전 (관리됨)
otel               = "1.42.1"  # opentelemetry-exporter-otlp (BOM 관리, 명시 생략 가능)
lokiLogback        = "1.5.2"   # com.github.loki4j:loki-logback-appender
```

> micrometer-tracing / micrometer-registry-prometheus / opentelemetry-exporter-otlp 는 **Spring Boot BOM이 버전을 관리**하므로 `version.ref` 없이 module만 선언해도 된다(권장). loki4j appender만 외부 라이브러리라 버전 명시 필요.

### 2-2. [libraries] 추가
```toml
# ── Phase 6 관측: 공통 ────────────────────────────────────────────────────────
micrometer-registry-prometheus = { module = "io.micrometer:micrometer-registry-prometheus" }
micrometer-tracing-bridge-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel" }
opentelemetry-exporter-otlp    = { module = "io.opentelemetry:opentelemetry-exporter-otlp" }
loki-logback-appender          = { module = "com.github.loki4j:loki-logback-appender", version.ref = "lokiLogback" }
```

### 2-3. 전 서비스 build.gradle.kts 공통 추가 블록
모든 13개 서비스 `dependencies {}`에 아래를 추가(actuator는 이미 있으면 중복 추가 금지):
```kotlin
    // ── Phase 6 관측 ──────────────────────────────────────────
    implementation(libs.spring.boot.starter.actuator)          // 이미 있으면 생략
    implementation(libs.micrometer.registry.prometheus)        // Prometheus 노출
    implementation(libs.micrometer.tracing.bridge.otel)        // 트레이싱 추상화 + OTel bridge
    implementation(libs.opentelemetry.exporter.otlp)           // 트레이스 OTLP 전송
    implementation(libs.loki.logback.appender)                 // Loki 로그 전송
```

> **api-gateway 주의**: 현재 actuator가 build.gradle.kts에 없을 수 있음 → 반드시 추가. Reactive 환경에서도 위 의존성 동일하게 동작(spring-boot-actuator-autoconfigure가 reactive 변형 자동 선택).

---

## 3. 서비스 공통 설정 (application.yml 표준 management 블록)

전 서비스 application.yml의 기존 `management:` 블록을 아래 **표준 블록으로 교체/병합**한다. 기존 `health.circuitbreakers.enabled`는 유지한다.

```yaml
management:
  endpoints:
    web:
      exposure:
        # 기존 health,info,circuitbreakers,metrics 에 prometheus 추가
        include: health,info,circuitbreakers,metrics,prometheus
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
  prometheus:
    metrics:
      export:
        enabled: true
  metrics:
    tags:
      application: ${spring.application.name}   # 모든 메트릭에 서비스명 태그
    distribution:
      percentiles-histogram:
        http.server.requests: true             # HTTP 지연 히스토그램(Grafana p95/p99용)
  tracing:
    enabled: true
    sampling:
      probability: 1.0                          # 개발용 100% 샘플링
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}

# 트레이스-로그 상관: 로그에 traceId/spanId 노출
logging:
  pattern:
    # Loki appender를 쓰면 logback-spring.xml에서 처리. 콘솔 가독성용으로도 유지.
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
  level:
    com.stockpulse: DEBUG
```

**서비스별 차이 처리**
- `spring.application.name`은 각 서비스에 이미 존재 → 그대로 사용.
- Kafka 소비자 lag은 별도 설정 불필요(Spring Kafka가 Micrometer로 `kafka.consumer.*` 메트릭 자동 노출). 단 **`micrometer` 바인딩이 동작하려면 consumer factory가 Micrometer를 인지**해야 함 → Spring Boot 3.3 자동설정이 처리하므로 추가 코드 불필요.
- Resilience4j CB 메트릭은 resilience4j-micrometer가 필요. resilience4j-spring-boot3에 전이 포함되어 `resilience4j.circuitbreaker.*` 메트릭이 actuator/metrics로 자동 노출됨(추가 의존성 불필요).

> **샘플링 0.1 등 운영값은 환경변수로**: `management.tracing.sampling.probability: ${TRACE_SAMPLING:1.0}` 형태로 외부화 권장(스펙은 기본 1.0).

---

## 3-1. Reactive 서비스 컨텍스트 전파 (api-gateway · market-collector · realtime-gateway 전용)

**문제**: Servlet 서비스는 요청이 한 스레드에 묶여 있어 Micrometer Tracing이 traceId/spanId를 ThreadLocal(MDC)에 자동으로 넣고, logback `%X{traceId}`가 그대로 찍힌다. 반면 **WebFlux/코루틴은 작업이 스레드를 넘나들며 실행(operator/dispatcher 전환)** 되므로 ThreadLocal 기반 MDC가 끊긴다. 추가 설정 없이는 reactive 3개 서비스의 로그에서 traceId가 항상 비어, §5-5 derivedField·§5-5 tracesToLogsV2 상관이 그 서비스들에서 동작하지 않는다.

### 3-1-1. Reactor Context 자동 전파 활성화 (3개 서비스 공통)
각 서비스의 `main()`에서 Spring 애플리케이션 기동 **전에** Reactor 훅을 켠다. Reactor 3.5+ 의 `enableAutomaticContextPropagation()`은 micrometer의 `context-propagation` 라이브러리와 연동해 Reactor Context ↔ ThreadLocal(MDC) 간 traceId를 자동 복원한다.

```kotlin
// 예: api-gateway / realtime-gateway / market-collector 의 *Application.kt
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication
class GatewayApplication

fun main(args: Array<String>) {
    Hooks.enableAutomaticContextPropagation()   // ★ runApplication 호출 전에
    runApplication<GatewayApplication>(*args)
}
```

> `@PostConstruct`로도 가능하나, 일부 초기 reactive 파이프라인이 훅 적용 전에 구성될 수 있어 **`main()` 최상단 호출을 표준으로 한다.**

### 3-1-2. 의존성 확인 (`io.micrometer:context-propagation`)
`enableAutomaticContextPropagation()`이 실제로 traceId를 복원하려면 클래스패스에 `io.micrometer:context-propagation`이 있어야 한다. 이는 **`micrometer-tracing` (§2-2의 `micrometer-tracing-bridge-otel`이 끌어옴) + Spring Boot 3.3 BOM**에 의해 전이 의존성으로 들어오는 것이 정상이다.

- backend-dev는 reactive 3개 서비스에서 `gradlew :services:api-gateway:dependencies --configuration runtimeClasspath` 출력으로 `io.micrometer:context-propagation` 존재를 **반드시 확인**한다.
- 누락 시 §2-2에 아래를 추가하고 3개 서비스 build.gradle.kts에 적용:
  ```toml
  micrometer-context-propagation = { module = "io.micrometer:context-propagation" }
  ```
  ```kotlin
  implementation(libs.micrometer.context.propagation)   // reactive 서비스에만
  ```

### 3-1-3. 코루틴 MDC 전파 (market-collector 한정)
market-collector는 KIS WebSocket 수신을 **코루틴 루프**(`KisWebSocketClient`)로 돌린다. 코루틴은 dispatcher가 스레드를 바꾸므로, 코루틴 내부 로깅에서 MDC를 유지하려면 `kotlinx-coroutines-slf4j`의 `MDCContext`를 코루틴 컨텍스트에 더해야 한다.

```toml
# libs.versions.toml [libraries] — coroutines 버전(1.8.1) 정렬
kotlinx-coroutines-slf4j = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-slf4j", version.ref = "coroutines" }
```
```kotlin
// market-collector build.gradle.kts
implementation(libs.kotlinx.coroutines.slf4j)

// KisWebSocketClient — 코루틴 기동부
withContext(Dispatchers.IO + MDCContext()) { /* 수신 루프 */ }
```

> **한계 명기 (proactive limit)**: KIS WebSocket 틱 수신 루프는 **외부 푸시가 트리거**라 inbound HTTP 트레이스(span)가 존재하지 않는다. 따라서 이 백그라운드 작업의 로그는 `MDCContext`를 써도 **상위 traceId가 없어 비어 있는 것이 정상**이다. (시작점이 있는 흐름 — 예: market-collector의 REST `MarketController` 요청 — 에서는 traceId가 정상 전파된다.) 즉 market-collector에서 trace↔log 상관은 "REST 요청 경로"에서만 기대하고, 틱 수집 백그라운드 로그는 상관 대상에서 제외한다.

---

## 4. 인프라 docker-compose 추가

별도 파일 **`docker-compose.observability.yml`** 로 분리한다(기존 인프라와 독립 기동·정리 용이). 동일 네트워크 `stockpulse-net`에 합류시켜 서비스명 DNS 통신을 가능케 한다.

> 기동: `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d`
> 관측만: `docker compose -f docker-compose.observability.yml up -d` (네트워크는 external 참조)

```yaml
# StockPulse — Phase 6 관측 스택 (Prometheus · Grafana · Loki · Tempo · Promtail)
# 기존 docker-compose.yml의 stockpulse-net 에 합류한다.
# 기동: docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d

services:
  # ── Prometheus (메트릭 수집) ────────────────────────────────────────────
  prometheus:
    image: prom/prometheus:v2.54.1
    container_name: stockpulse-prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.retention.time=7d"
    ports:
      - "${PROMETHEUS_PORT:-9090}:9090"
    volumes:
      - ./observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    extra_hosts:
      - "host.docker.internal:host-gateway"   # 호스트 bootRun 서비스 scrape용
    networks:
      - stockpulse-net

  # ── Loki (로그 집계) ────────────────────────────────────────────────────
  loki:
    image: grafana/loki:3.2.0
    container_name: stockpulse-loki
    command: ["-config.file=/etc/loki/loki-config.yml"]
    ports:
      - "${LOKI_PORT:-3100}:3100"
    volumes:
      - ./observability/loki/loki-config.yml:/etc/loki/loki-config.yml:ro
      - loki-data:/loki
    networks:
      - stockpulse-net

  # ── Promtail (컨테이너/파일 로그 → Loki) ─────────────────────────────────
  # 선택: 서비스가 docker로 뜰 때 컨테이너 로그를 긁어 Loki로 전송.
  # 호스트 bootRun + loki-logback-appender 사용 시에는 없어도 무방.
  promtail:
    image: grafana/promtail:3.2.0
    container_name: stockpulse-promtail
    command: ["-config.file=/etc/promtail/promtail-config.yml"]
    volumes:
      - ./observability/promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
    depends_on:
      - loki
    networks:
      - stockpulse-net

  # ── Tempo (분산 트레이싱) ───────────────────────────────────────────────
  tempo:
    image: grafana/tempo:2.6.1
    container_name: stockpulse-tempo
    command: ["-config.file=/etc/tempo/tempo.yml"]
    ports:
      - "${TEMPO_HTTP_PORT:-3200}:3200"   # Grafana 조회
      - "${TEMPO_OTLP_GRPC:-4317}:4317"   # OTLP gRPC 수신
      - "${TEMPO_OTLP_HTTP:-4318}:4318"   # OTLP HTTP 수신(대안)
    volumes:
      - ./observability/tempo/tempo.yml:/etc/tempo/tempo.yml:ro
      - tempo-data:/var/tempo
    networks:
      - stockpulse-net

  # ── Grafana (대시보드) ──────────────────────────────────────────────────
  grafana:
    image: grafana/grafana:11.2.2
    container_name: stockpulse-grafana
    ports:
      - "${GRAFANA_PORT:-3000}:3000"
    environment:
      GF_SECURITY_ADMIN_USER: "${GRAFANA_USER:-admin}"
      GF_SECURITY_ADMIN_PASSWORD: "${GRAFANA_PASSWORD:-admin}"
      GF_AUTH_ANONYMOUS_ENABLED: "true"          # 학습 편의: 익명 조회 허용
      GF_AUTH_ANONYMOUS_ORG_ROLE: "Viewer"
    volumes:
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
      - grafana-data:/var/lib/grafana
    depends_on:
      - prometheus
      - loki
      - tempo
    networks:
      - stockpulse-net

volumes:
  prometheus-data:
  loki-data:
  tempo-data:
  grafana-data:

# 기존 docker-compose.yml에서 생성한 네트워크를 재사용(external).
networks:
  stockpulse-net:
    external: true
    name: stockpulse_stockpulse-net   # ⚠️ compose 프로젝트명 prefix 확인 후 조정
```

> ⚠️ **네트워크 external 이름 주의**: docker compose는 네트워크명에 프로젝트명을 prefix한다(예: `stockpulse_stockpulse-net`). 단일 명령으로 `-f a -f b` 동시 기동하면 prefix 불일치 문제가 없으므로, **두 파일을 항상 함께 기동**하는 것을 기본으로 하고 `external` 대신 동일 `networks` 정의를 공유해도 된다. backend-dev는 실제 `docker network ls` 출력으로 이름을 확정할 것.

---

## 5. 설정 파일

배치 경로: `c:\study\StockPulse\observability\`

```
observability/
├── prometheus/prometheus.yml
├── loki/loki-config.yml
├── tempo/tempo.yml
├── promtail/promtail-config.yml
└── grafana/
    ├── provisioning/
    │   ├── datasources/datasources.yml
    │   └── dashboards/dashboards.yml
    └── dashboards/
        └── stockpulse-overview.json   (수동 import 또는 provisioning)
```

### 5-1. prometheus.yml (scrape config)
호스트 `bootRun` 시나리오 기준. 13개 서비스를 정적 타겟으로 등록.
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'stockpulse-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8080'   # api-gateway
          - 'host.docker.internal:8081'   # auth
          - 'host.docker.internal:8082'   # market-collector
          - 'host.docker.internal:8083'   # news-collector
          - 'host.docker.internal:8084'   # fundamentals-collector
          - 'host.docker.internal:8085'   # insight
          - 'host.docker.internal:8086'   # trading
          - 'host.docker.internal:8087'   # account
          - 'host.docker.internal:8088'   # portfolio
          - 'host.docker.internal:8089'   # ranking
          - 'host.docker.internal:8090'   # realtime-gateway
          - 'host.docker.internal:8091'   # discussion
          - 'host.docker.internal:8092'   # notification
    relabel_configs:
      # instance 라벨을 포트 기반으로 보기 좋게(선택)
      - source_labels: [__address__]
        target_label: instance

  # (대안) 서비스도 docker로 뜰 때: targets 를 'auth-service:8081' 형태로 교체
  - job_name: 'prometheus-self'
    static_configs:
      - targets: ['localhost:9090']
```

> **service 라벨은 메트릭에 이미 존재**: §3에서 `management.metrics.tags.application`을 줬으므로 모든 메트릭에 `application="<service>"` 라벨이 붙는다. 대시보드 변수는 이 라벨을 쓴다(타겟 instance가 host.docker.internal로 동일해도 구분 가능).

### 5-2. loki-config.yml (단일 노드 최소 구성)
```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  allow_structured_metadata: true
  retention_period: 168h   # 7일
```

### 5-3. tempo.yml (OTLP 수신 + 로컬 저장)
```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  max_block_duration: 5m

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/blocks
    wal:
      path: /var/tempo/wal

compactor:
  compaction:
    block_retention: 48h   # 트레이스 2일 보존(학습용)
```

### 5-4. promtail-config.yml (docker 컨테이너 로그 수집 — 선택)
```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 10s
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        regex: '/(stockpulse-.*)'
        target_label: 'container'
```

> 서비스가 호스트 bootRun + **loki-logback-appender** 를 쓰는 기본 시나리오에서는 promtail 없이도 로그가 Loki에 들어간다. promtail은 "서비스도 docker로 띄울 때" 보조 경로로 둔다.

### 5-5. grafana/provisioning/datasources/datasources.yml
trace↔log 상관 포함.
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    jsonData:
      derivedFields:
        - name: TraceID
          matcherRegex: '"traceId":"(\w+)"'   # logback JSON 패턴에 맞춤
          url: '$${__value.raw}'
          datasourceUid: tempo

  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    uid: tempo
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
      serviceMap:
        datasourceUid: prometheus
```

> Grafana 환경변수 `$`는 `$$`로 이스케이프(compose 변수 확장 방지). provisioning 파일에서도 derivedFields url의 `${__value.raw}`는 Grafana 내부 변수이므로 `$$`로 적는다.

### 5-6. grafana/provisioning/dashboards/dashboards.yml (대시보드 자동 로드)
```yaml
apiVersion: 1

providers:
  - name: 'StockPulse'
    orgId: 1
    folder: 'StockPulse'
    type: file
    disableDeletion: false
    editable: true
    options:
      path: /var/lib/grafana/dashboards
```

### 5-7. 대시보드 (2가지 방법 모두 명시)

**방법 A — JSON provisioning (권장, 재현 가능)**
- `grafana/dashboards/stockpulse-overview.json` 에 대시보드 JSON 배치 → 기동 시 자동 로드.
- 패널 구성(데이터소스 변수 `application` 라벨 기준):
  | 패널 | PromQL(예시) |
  |------|--------------|
  | 서비스 헬스(UP) | `up{job="stockpulse-services"}` |
  | JVM 힙 사용 | `sum by(application)(jvm_memory_used_bytes{area="heap"})` |
  | HTTP 요청률(RPS) | `sum by(application)(rate(http_server_requests_seconds_count[1m]))` |
  | HTTP p95 지연 | `histogram_quantile(0.95, sum by(le,application)(rate(http_server_requests_seconds_bucket[5m])))` |
  | HTTP 5xx 에러율 | `sum by(application)(rate(http_server_requests_seconds_count{status=~"5.."}[1m]))` |
  | Kafka consumer lag | `sum by(application,client_id)(kafka_consumer_fetch_manager_records_lag)` |
  | Resilience4j CB 상태 | `resilience4j_circuitbreaker_state` (state=open/closed/half_open) |
  | CB 실패율 | `resilience4j_circuitbreaker_failure_rate` |

**방법 B — 수동 import**
- Grafana UI → Dashboards → Import.
- 검증용으로 커뮤니티 대시보드 ID 활용 가능: **JVM (Micrometer) = ID 4701**, **Spring Boot 3.x Statistics 등**. import 후 데이터소스를 Prometheus로 지정.
- Kafka/Resilience4j는 위 PromQL로 패널 직접 추가.

> backend-dev는 **방법 A의 stockpulse-overview.json을 직접 작성**(최소 위 8개 패널)하고, 방법 B는 README에 import 절차로 문서화한다.

---

## 6. 구조적 로깅 (logback-spring.xml)

전 서비스 `src/main/resources/logback-spring.xml` 신규 작성(공통 템플릿, 서비스명만 application name으로 자동 주입). **JSON 구조적 로깅 + traceId/spanId + Loki appender**.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <springProperty scope="context" name="appName" source="spring.application.name"/>
    <property name="LOKI_URL" value="${LOKI_URL:-http://localhost:3100/loki/api/v1/push}"/>

    <!-- 콘솔: traceId/spanId 포함 (개발 가독성) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [${appName:-},%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Loki: JSON 라벨 + 메시지(traceId 포함) -->
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>${LOKI_URL}</url>
        </http>
        <format>
            <label>
                <pattern>application=${appName},host=${HOSTNAME},level=%level</pattern>
            </label>
            <message>
                <!-- traceId/spanId를 메시지 JSON에 포함 → Loki derivedField 매칭 -->
                <pattern>{"ts":"%d{yyyy-MM-dd'T'HH:mm:ss.SSS}","level":"%level","logger":"%logger","traceId":"%X{traceId:-}","spanId":"%X{spanId:-}","msg":"%message"}</pattern>
            </message>
        </format>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>
    </root>
    <logger name="com.stockpulse" level="DEBUG"/>
</configuration>
```

> - loki4j는 `<label>`(인덱싱되는 라벨, 카디널리티 낮게 유지) vs `<message>`(본문) 구분이 핵심. **traceId는 라벨이 아니라 메시지**에 넣어야 카디널리티 폭증을 막는다.
> - §5-5 datasources의 derivedField `matcherRegex`가 이 메시지 JSON의 `"traceId":"..."` 와 일치해야 Loki→Tempo 점프가 동작한다.
> - api-gateway 등 Reactive 서비스도 동일 파일 적용 가능(logback은 런타임 공통).

---

## 7. DoD — 검증 항목 (qa 담당)

| # | 항목 | 검증 방법 | 합격 기준 |
|---|------|-----------|-----------|
| 1 | 의존성 빌드 | `gradlew build` 전 서비스 | 4개 의존성 추가 후 컴파일 성공 |
| 2 | actuator prometheus 노출 | `curl localhost:8086/actuator/prometheus` | `jvm_`, `http_server_requests_`, `resilience4j_` 메트릭 라인 존재 |
| 3 | Prometheus 타겟 UP | `http://localhost:9090/targets` | 기동된 서비스가 `UP` (실행 안 한 서비스는 DOWN 허용) |
| 4 | application 라벨 | Prometheus `up` 쿼리 | 메트릭에 `application="<service>"` 라벨 존재 |
| 5 | Grafana 접속 | `http://localhost:3000` | 로그인(admin) 또는 익명 Viewer 진입 |
| 6 | datasource 3종 | Grafana → Connections | Prometheus/Loki/Tempo 모두 "Test" 성공 |
| 7 | 대시보드 로드 | Grafana → Dashboards | stockpulse-overview 패널에 데이터 렌더 |
| 8 | HTTP 지연 패널 | 서비스에 REST 요청 몇 번 발생 후 | p95 패널에 값 표시 |
| 9 | Kafka lag 패널 | consumer 동작 중 | `kafka_consumer_fetch_manager_records_lag` 노출(소비자 있는 서비스) |
| 10 | CB 상태 패널 | resilience4j 적용 서비스(trading/market) | `resilience4j_circuitbreaker_state` 노출 |
| 11 | 로그 수집 | Grafana → Explore → Loki, `{application="trading-service"}` | 로그 라인 조회됨 |
| 12 | 로그에 traceId | Loki 로그 본문 | `"traceId":"..."` 비어있지 않음(요청 트래픽 발생 시) |
| 13 | 트레이스 수신 | Grafana → Explore → Tempo, Search | 트레이스 1건 이상(Gateway→하위 서비스 호출 시 다중 span) |
| 14 | 트레이스 전파 | Gateway 경유 REST 1회 | traceId가 Gateway~하위 서비스 span에 동일하게 전파 |
| 15 | trace↔log 점프 | Tempo 트레이스에서 "Logs for this span" | 해당 traceId 로그로 이동 성공 |
| 16 | **reactive 서비스 traceId 로그 전파** | api-gateway 경유 REST 1회 발생 후 Loki에서 `{application="api-gateway"}` (또는 realtime-gateway) 로그 조회 | 로그 본문 `"traceId"`가 **비어있지 않음**. §3-1 Reactor Context 전파가 동작함을 입증(미적용 시 reactive 서비스만 traceId 공백) |

> 최소 합격선(학습/포트폴리오): **#3, #5, #6, #7, #11, #13** 통과 = 4스택 동작 확인. reactive 상관까지 보장하려면 **#16** 추가. 나머지는 트래픽 발생 시 자연 충족.
> ⚠️ #16은 reactive/servlet 회귀 구분점이다. servlet 서비스(#12) traceId는 찍히는데 reactive 서비스(#16)가 비면 §3-1-1/§3-1-2 누락이다.

---

## 8. 구현 파일 목록 (backend-dev 작업 체크리스트)

**수정**
- [ ] `gradle/libs.versions.toml` — §2-1 versions, §2-2 libraries 추가
- [ ] 13개 `services/*/build.gradle.kts` — §2-3 공통 의존성 블록 추가(actuator 중복 주의, api-gateway는 actuator 신규)
- [ ] 13개 `services/*/src/main/resources/application.yml` — §3 표준 management 블록으로 교체/병합
- [ ] **reactive 3개** `*Application.kt`(api-gateway, market-collector, realtime-gateway) — §3-1-1 `Hooks.enableAutomaticContextPropagation()` 추가
- [ ] **reactive 3개** runtimeClasspath에 `io.micrometer:context-propagation` 존재 확인, 누락 시 §3-1-2 의존성 추가
- [ ] market-collector — §3-1-3 `kotlinx-coroutines-slf4j` 의존성 + `KisWebSocketClient` 코루틴에 `MDCContext()` 적용

**신규**
- [ ] 13개 `services/*/src/main/resources/logback-spring.xml` — §6 공통 템플릿
- [ ] `docker-compose.observability.yml` — §4
- [ ] `observability/prometheus/prometheus.yml` — §5-1
- [ ] `observability/loki/loki-config.yml` — §5-2
- [ ] `observability/tempo/tempo.yml` — §5-3
- [ ] `observability/promtail/promtail-config.yml` — §5-4 (선택)
- [ ] `observability/grafana/provisioning/datasources/datasources.yml` — §5-5
- [ ] `observability/grafana/provisioning/dashboards/dashboards.yml` — §5-6
- [ ] `observability/grafana/dashboards/stockpulse-overview.json` — §5-7 방법 A (8패널)

**문서 동기화 (구현 후 architect/spec)**
- [ ] `docs/09_개발환경.md` — 관측 스택 기동 명령·접속 주소(Grafana 3000 등) 채우기
- [ ] `docs/02_시스템아키텍처.md` — §0-1 서비스명 불일치(Community→discussion, Notification→notification) 정정 검토
- [ ] `.env.example` — 관측 포트/Grafana 자격증명/`OTEL_EXPORTER_OTLP_ENDPOINT`/`LOKI_URL` 추가

---

## 부록 A. 설계 한계 · 사전 경고 (proactive limits)

- **단일 노드·로컬 스토리지**: Loki/Tempo 모두 filesystem 백엔드, replication 1. 학습용으로 충분하나 운영 등급 아님(데이터 유실·확장 불가). 의도된 트레이드오프(docs/07: "경량·저비용").
- **샘플링 100%**: 트레이스 부하가 크지만 13개 서비스 로컬 트래픽 수준에선 무해. 운영 전환 시 `TRACE_SAMPLING` 0.1로 낮출 것.
- **host.docker.internal scrape 의존**: Windows/Mac Docker Desktop에서 동작. 순수 Linux면 `extra_hosts: host-gateway` 매핑 필수(§4에 포함됨).
- **네트워크 external 이름**: compose 프로젝트 prefix 때문에 단독 기동 시 이름 불일치 가능 → 두 compose 파일 **동시 기동**을 기본 운영 방식으로 권장.
- **Kafka consumer lag 메트릭**: `kafka_consumer_fetch_manager_records_lag`는 소비자가 실제 토픽을 구독·폴링 중일 때만 노출. 미동작 서비스는 패널 공백이 정상.
- **reactive 백그라운드 작업 traceId 공백**: §3-1-3대로 market-collector의 KIS WebSocket 틱 수집 루프는 inbound 트레이스 시작점이 없어 traceId가 빈다(정상). reactive 서비스의 trace↔log 상관은 inbound 흐름(REST/WS upgrade)이 있는 경로에서만 보장된다.
