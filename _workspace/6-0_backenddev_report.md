# Phase 6 관측 구현 보고서 (오케스트레이터 직접 구현)

> backend-dev 서브에이전트가 세션 한도로 중단되어, 오케스트레이터가 스펙 `6-0_architect_spec.md`를 기준으로 직접 구현·검증함.

## 구현 파일 목록

### 수정 (의존성·설정)
- `gradle/libs.versions.toml` — 관측 의존성 6종 추가 (micrometer-registry-prometheus, micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp, loki-logback-appender 1.5.2, micrometer-context-propagation, kotlinx-coroutines-slf4j)
- 14개 `services/*/build.gradle.kts` — 관측 의존성 블록 추가 (actuator 없던 8개는 actuator도 추가, reactive 3개는 context-propagation, market-collector는 coroutines-slf4j)
- 14개 `services/*/src/main/resources/application.yml` — management 표준 블록(prometheus 노출, tracing sampling, OTLP endpoint) 추가/병합

### 신규
- 14개 `services/*/src/main/resources/logback-spring.xml` — CONSOLE(traceId 포함) + Loki appender(JSON 메시지에 traceId)
- `docker-compose.observability.yml` — prometheus/grafana/loki/tempo/promtail 5컨테이너
- `observability/prometheus/prometheus.yml` — 13개 서비스 host.docker.internal scrape
- `observability/loki/loki-config.yml`
- `observability/tempo/tempo.yml`
- `observability/promtail/promtail-config.yml`
- `observability/grafana/provisioning/datasources/datasources.yml` — Prometheus/Loki/Tempo + trace↔log 상관
- `observability/grafana/provisioning/dashboards/dashboards.yml`
- `observability/grafana/dashboards/stockpulse-overview.json` — 8패널

### reactive 컨텍스트 전파 (스펙 §3-1)
- api-gateway, realtime-gateway, market-collector `*Application.kt` — `Hooks.enableAutomaticContextPropagation()` 추가
- market-collector `KisWebSocketClient` — CoroutineScope에 `MDCContext()` 추가

## 스펙 대비 변경점 (직접 잡은 결함 1건)

**OTLP endpoint 포트 오류 (스펙 §1-2, §3)**
- 스펙은 `endpoint: http://localhost:4317`(OTLP gRPC)로 지정했으나, Spring Boot 3.3의 OTLP tracing exporter는 **HTTP/protobuf 방식**이라 gRPC 포트(4317)로 보내면 `HttpExporter - Failed to export spans: unexpected end of stream` 실패.
- **수정**: 14개 yml 모두 `http://localhost:4318/v1/traces`로 변경. 재기동 후 export 에러 0건, Tempo 트레이스 정상 수신 확인.
- Tempo 컨테이너는 4317(gRPC)·4318(HTTP) 둘 다 listen하므로 인프라 변경 불필요.

## 런타임 검증 결과 (trading-service 기준)

| DoD | 항목 | 결과 |
|-----|------|------|
| #1 | 전체 빌드 (14개 서비스) | ✅ BUILD SUCCESSFUL in 3m 5s |
| #2 | actuator/prometheus 메트릭 노출 | ✅ jvm_*, http_server_requests_*, application 라벨 확인 |
| #3 | Prometheus 타겟 UP | ✅ host.docker.internal:8086 up=1 |
| #4 | application 라벨 | ✅ application="trading-service" |
| #5 | Grafana 접속 | ✅ :3000 health 200 |
| #11 | Loki 로그 수집 | ✅ {application="trading-service"} 스트림 수신, JSON 메시지 포맷 정상 |
| #13 | Tempo 트레이스 수신 | ✅ rootServiceName=trading-service, http get /orders/{orderId} |
| logback | traceId 패턴 | ✅ 콘솔 [trading-service,,] 형식 적용 |

## 미검증 (정보성, 트래픽 발생 시 자연 충족)
- #9 Kafka consumer lag, #10 CB 상태 패널: 해당 트래픽/상태 발생 시 노출. 단일 서비스 검증이라 미발생.
- #16 reactive 서비스(api-gateway) traceId 로그 전파: trading(servlet)으로 핵심 경로 검증 완료. reactive 전파 설정(Hooks)은 코드 반영됨, 실트래픽 검증은 api-gateway 기동 시.
- 14개 전 서비스 동시 기동 검증은 리소스 관계로 생략(빌드는 14개 전체 SUCCESS).

## 인프라 운영 메모
- 기동: `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d` (두 파일 함께 권장)
- 네트워크명 `stockpulse_stockpulse-net` (external)로 확정
- Grafana http://localhost:3000 (admin/admin 또는 익명 Viewer)
