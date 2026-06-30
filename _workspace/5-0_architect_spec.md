# Phase 5 안정성 구현 스펙

> 목표: 외부 API(KIS)·Kafka·Redis 장애를 격리/복구하고, 일관된 에러 응답을 제공한다.
> 원칙: **이미 구현된 코드 수정 최소화 — 어노테이션 추가 위주**, fallback 메서드/설정 추가 위주.
> 근거: `docs/07_기술선택이유.md` §3 — Resilience4j는 외부 API(KIS·네이버·DART) 장애 격리용으로 이미 채택됨(CB·retry·timeout·bulkhead). Service Mesh를 제외하고 앱 계층에서 처리한다는 결정과 일치(§4).

---

## 1. Resilience4j 적용 대상 및 설정값

Resilience4j `spring-boot-starter-resilience4j`(AOP) 기반 `@CircuitBreaker` / `@Retry` / `@TimeLimiter` 어노테이션을 사용한다.

> 주의: `@TimeLimiter`는 반환 타입이 `CompletableFuture` 또는 reactive 타입일 때만 동작한다. 코루틴 `suspend` 함수는 Resilience4j Kotlin 확장(`io.github.resilience4j:resilience4j-kotlin`)의 `executeSuspendFunction` 또는 코루틴 데코레이터를 사용해야 AOP 어노테이션과 동등하게 동작한다. 본 스펙에서는 **KIS(코루틴 suspend)는 Kotlin 확장 데코레이터**, **나머지 동기 코드는 AOP 어노테이션**을 적용한다.

### 1-1. market-collector — `KisApprovalClient` (KIS Approval Key REST 호출)

대상: `KisApprovalClient.getApprovalKey()` (suspend 함수, WebClient 호출).

구현 방식: AOP 어노테이션 대신 **resilience4j-kotlin 코루틴 데코레이터**로 감싼다. CB·Retry·TimeLimiter 레지스트리를 주입받아 `executeSuspendFunction`으로 실행.

```kotlin
// KisApprovalClient (수정 — 생성자에 레지스트리 주입 + getApprovalKey 본문 데코레이트)
class KisApprovalClient(
    ...,
    private val cbRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val timeLimiterRegistry: TimeLimiterRegistry,
) {
    private val cb = cbRegistry.circuitBreaker("kisApproval")
    private val retry = retryRegistry.retry("kisApproval")
    private val timeLimiter = timeLimiterRegistry.timeLimiter("kisApproval")

    suspend fun getApprovalKey(): String =
        cb.executeSuspendFunction {
            retry.executeSuspendFunction {
                timeLimiter.executeSuspendFunction {
                    requestApprovalKey()   // 기존 본문을 private 함수로 추출
                }
            }
        }
}
```

설정값(application.yml, market-collector):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      kisApproval:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50          # 50% 실패 시 OPEN
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
  retry:
    instances:
      kisApproval:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2   # 1s → 2s → 4s
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - org.springframework.web.reactive.function.client.WebClientResponseException$ServiceUnavailable
          - java.util.concurrent.TimeoutException
  timelimiter:
    instances:
      kisApproval:
        timeout-duration: 5s
        cancel-running-future: true
```

근거: Approval Key 발급은 저빈도(앱 기동·키 만료 시)·치명적 호출이므로 재시도 3회·지수 backoff로 일시 장애를 흡수하고, 반복 실패 시 CB OPEN으로 빠르게 차단한다.

### 1-2. notification-service — `AlertEvaluator.evaluate()` (Kafka `market.tick` 소비 중 예외)

대상: `TickConsumer.consume()` → `AlertEvaluator.evaluate(message)`. DB 조회/저장 중 일시 예외(DB 커넥션 순단 등)를 retry로 흡수하고, 영구 실패는 §2 DLT로 보낸다.

> Kafka 소비 경로의 1차 방어는 **§2 DefaultErrorHandler(FixedBackOff) + DLT**이다. Resilience4j는 평가 로직 내부 `evaluate()`에 `@Retry`만 적용해 transient DB 오류를 흡수한다(CB는 소비자 스레드를 OPEN으로 막으면 컨슈머가 멈출 수 있어 적용하지 않음).

```kotlin
// AlertEvaluator.evaluate 에 어노테이션 추가 (본문 변경 없음)
@Retry(name = "alertEval")
@Transactional
fun evaluate(tickJson: String) { ... }
```

`TickConsumer.consume()`의 기존 try/catch(23~24행)는 제거하여 예외를 컨테이너 ErrorHandler로 전파시킨다(§2 DLT 동작 전제). 제거 후:

```kotlin
@KafkaListener(topics = ["market.tick"], groupId = "notification-service")
fun consume(message: String) {
    log.debug("market.tick 수신: {}", message)
    alertEvaluator.evaluate(message)   // 예외는 ErrorHandler → DLT 로 위임
}
```

설정값(application.yml, notification-service):

```yaml
resilience4j:
  retry:
    instances:
      alertEval:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - org.springframework.dao.TransientDataAccessException
          - org.springframework.dao.QueryTimeoutException
        ignore-exceptions:
          - com.fasterxml.jackson.core.JsonProcessingException   # 파싱 오류는 재시도 무의미 → 바로 DLT
```

### 1-3. trading-service — `TradingService` Saga 단계별 Kafka publish 실패

대상: `TradingService.publishOrderEvent()` 내부 `kafkaTemplate.send("order.events", ...)` (115행). Saga 각 단계 이벤트 발행이 일시 실패하면 보상이 누락되므로 retry로 보장한다.

```kotlin
// publishOrderEvent 에 @Retry 추가 (private → 호출 가능하도록 별도 public 위임 메서드로 분리하거나
// resilience4j AOP는 같은 클래스 self-invocation 미동작 → KafkaPublisher 컴포넌트로 추출)
```

self-invocation 문제 해결을 위해 **`OrderEventPublisher` 컴포넌트로 발행 책임을 분리**한다(어노테이션 AOP가 동작하도록):

```kotlin
@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    @CircuitBreaker(name = "orderEvents")
    @Retry(name = "orderEvents")
    fun publish(key: String, payload: String) {
        kafkaTemplate.send("order.events", key, payload).get(3, TimeUnit.SECONDS)
        // .get() 으로 발행 결과를 동기 확인 → 실패 시 예외 → @Retry 동작
    }
}
```

`TradingService.publishOrderEvent()`는 위 컴포넌트를 주입받아 `orderEventPublisher.publish(orderId.toString(), payload)` 호출로 교체한다(직렬화는 유지 또는 publisher로 이전).

설정값(application.yml, trading-service):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      orderEvents:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 20s
  retry:
    instances:
      orderEvents:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.apache.kafka.common.errors.TimeoutException
          - org.springframework.kafka.KafkaException
          - java.util.concurrent.TimeoutException
```

> 주: Saga 이벤트는 `@Transactional` 메서드 내부에서 발행된다. 발행이 retry 끝에 최종 실패하면 예외가 트랜잭션을 롤백시켜 DB 변경과 이벤트 미발행 상태가 일관되게 유지된다(향후 Outbox 도입 전까지의 보장).

### 설정값 요약표

| 서비스 | instance | CB(failure/window/open-wait) | Retry(attempts/backoff) | Timeout |
|--------|----------|------------------------------|--------------------------|---------|
| market-collector | kisApproval | 50% / 10 / 30s | 3 / 1s·x2 | 5s |
| notification-service | alertEval | (미적용) | 3 / 500ms | (소비자 컨테이너) |
| trading-service | orderEvents | 50% / 20 / 20s | 3 / 1s·x2 | 3s(send.get) |
| market-collector | redis | 50% / 10 / 10s | (미적용, fallback 즉시) | — |
| ranking-service | redis | 50% / 10 / 10s | (미적용, fallback 즉시) | — |

---

## 2. Kafka DLT 설정

토픽명 규칙: `{원본토픽}.DLT` → `market.tick.DLT`, `order.events.DLT`.

방식: **`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`** (전역 컨테이너 ErrorHandler). `@RetryableTopic` 대신 선택한 이유 — 토픽 자동 생성·재시도 토픽 난립을 피하고 컨슈머별로 명시적 backoff/DLT 라우팅을 제어하기 위함.

### 2-1. notification-service (`market.tick` 소비)

```kotlin
@Configuration
class KafkaErrorHandlingConfig {

    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition("${record.topic()}.DLT", record.partition())
        }
        // 재시도 3회(1초 간격) 후 DLT 발행
        val backOff = FixedBackOff(1_000L, 3L)
        return DefaultErrorHandler(recoverer, backOff).apply {
            // 재시도 무의미한 예외는 즉시 DLT
            addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException::class.java,
                IllegalArgumentException::class.java,
            )
        }
    }
}
```

`ConcurrentKafkaListenerContainerFactory`에 `setCommonErrorHandler(errorHandler)`를 설정한다(기존 factory 빈이 있으면 주입, 없으면 Boot 자동설정에 ErrorHandler 빈만 등록해도 적용됨).

DLT 토픽: `market.tick.DLT` (파티션 수는 원본과 동일하게 자동/사전 생성).

### 2-2. trading-service (`order.events` 소비 측)

동일 패턴의 `DefaultErrorHandler` 빈을 trading-service(및 account/portfolio 소비자)에 등록 → 실패 시 `order.events.DLT` 발행. Saga 이벤트는 멱등 처리(`markProcessed`)가 이미 있으므로 재시도 안전.

> DLT 토픽명 매핑은 `record.topic() + ".DLT"`로 일반화되어 모든 컨슈머에 동일 규칙 적용.

### 2-3. DLT 운영

- DLT 메시지는 별도 모니터링/수동 재처리 대상(Phase 6 관측성에서 Grafana alert 연동).
- DLT 헤더에 원본 토픽·예외 메시지·스택(자동 기록)이 포함되어 원인 추적 가능.

---

## 3. Redis Fallback 설계

패턴: `@CircuitBreaker(name="redis", fallbackMethod="...")`. Redis 연결 실패(`RedisConnectionFailureException` 등) 시 CB가 fallback을 호출, DB 직접 조회로 대체한다.

> fallbackMethod는 **원본 메서드와 동일 시그니처 + 마지막 파라미터로 `Throwable` 추가**여야 한다. 같은 클래스 내 메서드 가능(AOP는 CB의 경우 self-fallback 동작; 단 CB 어노테이션이 붙은 메서드는 외부에서 호출되어야 프록시 적용 — controller→service 호출이므로 충족).

### 3-1. market-collector — `getQuote`

현재 `MarketController.getQuote` → `tickService.getQuote(symbol)`(Redis 캐시 조회로 추정). `TickService.getQuote`에 CB+fallback 적용. DB 대체는 TimescaleDB의 최신 틱 조회.

```kotlin
@CircuitBreaker(name = "redis", fallbackMethod = "getQuoteFromDb")
fun getQuote(symbol: String): QuoteResponse? {
    // 기존 Redis 캐시 조회 로직 (변경 없음)
}

// fallback: DB(TimescaleDB) 최신 틱 직접 조회
fun getQuoteFromDb(symbol: String, t: Throwable): QuoteResponse? {
    log.warn("Redis 장애 → DB fallback: symbol={} cause={}", symbol, t.message)
    return tickRepository.findLatestBySymbol(symbol)?.toQuoteResponse()
}
```

대체 쿼리(JPA, TimescaleDB `ticks` 테이블 기준):

```kotlin
// TickRepository
@Query("""
    SELECT t FROM TickEntity t
    WHERE t.symbol = :symbol
    ORDER BY t.ts DESC
    LIMIT 1
""")
fun findLatestBySymbol(symbol: String): TickEntity?
```

(JPA가 네이티브 LIMIT 미지원이면 `findFirstBySymbolOrderByTsDesc(symbol)` 파생 쿼리로 대체.)

### 3-2. ranking-service — `getTopRanking`

`RankingService.getTopRanking(size)`(현재 Redis Sorted Set 조회, 15~21행)에 CB+fallback. DB 대체는 모의투자 계좌의 수익률을 정렬 조회.

```kotlin
@CircuitBreaker(name = "redis", fallbackMethod = "getTopRankingFromDb")
fun getTopRanking(size: Long): List<Map<String, Any>> {
    // 기존 reverseRangeWithScores 로직 (변경 없음)
}

fun getTopRankingFromDb(size: Long, t: Throwable): List<Map<String, Any>> {
    log.warn("Redis 장애 → DB fallback ranking: cause={}", t.message)
    return rankingQueryRepository.findTopReturnRates(size)
        .mapIndexed { idx, r ->
            mapOf<String, Any>("rank" to idx + 1, "userId" to r.userId.toString(), "returnRate" to r.returnRate)
        }
}
```

대체 쿼리(수익률 원천 테이블 — account/portfolio 수익률 집계 뷰 또는 ranking 스냅샷 테이블 기준):

```sql
SELECT user_id, return_rate
FROM ranking_snapshot          -- 또는 account 수익률 집계 뷰
ORDER BY return_rate DESC
LIMIT :size
```

> ranking-service가 자체 DB를 갖지 않는 경우: (a) 주기적으로 Redis ZSet을 DB 스냅샷 테이블에 백업해 fallback 소스로 사용하거나, (b) account-service 조회 API를 fallback 경로로 둔다. 본 스펙은 (a) `ranking_snapshot` 테이블을 권장(Redis 장애 시 네트워크 추가 hop 없이 로컬 DB로 응답).

설정값(application.yml, market-collector / ranking-service 공통):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      redis:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        record-exceptions:
          - org.springframework.data.redis.RedisConnectionFailureException
          - org.springframework.dao.QueryTimeoutException
          - io.lettuce.core.RedisCommandTimeoutException
```

---

## 4. Global Exception Handler

`@RestControllerAdvice` — **common 모듈이 아닌 각 서비스에 개별 파일**로 추가(common은 Spring MVC 의존성 없음). 단, 응답 shape은 공통 `ApiResponse.fail(code, message)`를 사용한다.

각 서비스에 동일 내용의 `GlobalExceptionHandler.kt`를 배치(서비스별 패키지). 처리 대상·매핑은 공통:

| 예외 | HTTP 상태 | code | 비고 |
|------|-----------|------|------|
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | 요청 검증 실패 |
| `IllegalArgumentException` | 400 | `BAD_REQUEST` | |
| `NoSuchElementException` / 도메인 NotFound | 404 | `NOT_FOUND` | |
| `io.github.resilience4j.circuitbreaker.CallNotPermittedException` | 503 | `SERVICE_UNAVAILABLE` | CB OPEN — 일시적 차단 |
| `java.util.concurrent.TimeoutException` | 504 | `TIMEOUT` | TimeLimiter |
| `org.springframework.web.bind.MissingServletRequestParameterException` | 400 | `MISSING_PARAM` | |
| `Exception` (fallback) | 500 | `INTERNAL_ERROR` | message는 일반화(내부 노출 금지) |

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(e: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val msg = e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        return ApiResponse.fail("VALIDATION_ERROR", msg)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: IllegalArgumentException) =
        ApiResponse.fail<Nothing>("BAD_REQUEST", e.message ?: "잘못된 요청")

    @ExceptionHandler(CallNotPermittedException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleCbOpen(e: CallNotPermittedException) =
        ApiResponse.fail<Nothing>("SERVICE_UNAVAILABLE", "일시적으로 서비스를 이용할 수 없습니다.")

    @ExceptionHandler(TimeoutException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun handleTimeout(e: TimeoutException) =
        ApiResponse.fail<Nothing>("TIMEOUT", "요청 시간이 초과되었습니다.")

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGeneric(e: Exception): ApiResponse<Nothing> {
        log.error("처리되지 않은 예외", e)
        return ApiResponse.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다.")
    }
}
```

응답 shape (`ApiResponse.fail`):

```json
{ "success": false, "data": null, "error": { "code": "SERVICE_UNAVAILABLE", "message": "일시적으로 서비스를 이용할 수 없습니다." } }
```

> 적용 서비스: REST 컨트롤러를 가진 서비스 전부 — 최소 **market-collector, ranking-service, trading-service, notification-service**(controller 존재 시). webflux 기반 서비스(api-gateway 등)는 `@RestControllerAdvice`가 동일 동작하나 reactive 반환 타입 주의(본 Phase 대상 4개는 MVC `spring-boot-starter-web` 기반).

---

## 5. 의존성 추가

### 5-1. `gradle/libs.versions.toml`

`[versions]`에 추가:
```toml
resilience4j = "2.2.0"      # Spring Boot 3.3.x 호환
```

`[libraries]`에 추가:
```toml
# ── Phase 5 안정성 (Resilience4j) ───────────────────────────────────────────
resilience4j-spring-boot3 = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }
resilience4j-kotlin       = { module = "io.github.resilience4j:resilience4j-kotlin",       version.ref = "resilience4j" }
spring-boot-starter-aop   = { module = "org.springframework.boot:spring-boot-starter-aop" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }  # CB 상태 노출(Phase 6 연계)
```

> `resilience4j-spring-boot3`는 AOP 어노테이션(`@CircuitBreaker` 등) 활성화에 `spring-boot-starter-aop`를 필요로 한다. actuator는 CB/Retry 메트릭 노출용(선택이나 DoD 검증 편의를 위해 권장).

### 5-2. `build.gradle.kts` 수정 서비스

| 서비스 | 추가 의존성 |
|--------|-------------|
| market-collector | `resilience4j-spring-boot3`, `resilience4j-kotlin`, `spring-boot-starter-aop`, (actuator) |
| notification-service | `resilience4j-spring-boot3`, `spring-boot-starter-aop`, (actuator) |
| trading-service | `resilience4j-spring-boot3`, `spring-boot-starter-aop`, (actuator) |
| ranking-service | `resilience4j-spring-boot3`, `spring-boot-starter-aop`, (actuator) |

추가 형식 예(market-collector):
```kotlin
implementation(libs.resilience4j.spring.boot3)
implementation(libs.resilience4j.kotlin)
implementation(libs.spring.boot.starter.aop)
implementation(libs.spring.boot.starter.actuator)
```

> common 모듈에는 어떤 Resilience4j/MVC 의존성도 추가하지 않는다(현 분리 원칙 유지).

---

## 6. DoD — 런타임 검증 항목

| # | 장애 주입 시나리오 | 기대 동작 |
|---|--------------------|-----------|
| D1 | KIS Approval 엔드포인트를 잘못된 URL/포트로 변경 후 `getApprovalKey()` 호출 | retry 3회(1s→2s→4s) 후 실패, 5회 누적 시 CB `kisApproval` OPEN, 30s 후 HALF_OPEN. actuator `/actuator/circuitbreakers`에 상태 노출 |
| D2 | KIS 응답을 5s 이상 지연(지연 mock) | `TimeoutException` 발생, 호출 취소 |
| D3 | `market.tick`에 깨진 JSON 발행 | `JsonProcessingException` → 재시도 없이 즉시 `market.tick.DLT`로 이동(원본 1건, DLT 1건) |
| D4 | `market.tick` 처리 중 DB 일시 장애(TransientDataAccessException) | `@Retry alertEval` 3회 후 성공 시 정상 처리, 계속 실패 시 컨테이너 backoff 3회 후 `market.tick.DLT` |
| D5 | Kafka 브로커 다운 상태에서 `placeOrder` | `OrderEventPublisher.publish` retry 3회 실패 → 예외 → `placeOrder` 트랜잭션 롤백(주문/Saga 미저장), CB `orderEvents` 누적 실패 시 OPEN |
| D6 | Redis 컨테이너 중지 후 `GET /market/quote/005930` | `redis` CB fallback → DB(TimescaleDB) 최신 틱으로 정상 200 응답, 로그에 "Redis 장애 → DB fallback" |
| D7 | Redis 중지 후 `GET /ranking/top?size=10` | `redis` CB fallback → `ranking_snapshot` DB 조회로 200 응답 |
| D8 | Redis 반복 실패로 CB OPEN | 이후 호출은 Redis 시도 없이 즉시 fallback(레이턴시 감소), 10s 후 HALF_OPEN 복구 시도 |
| D9 | 잘못된 요청(검증 실패) 전송 | 400 + `{success:false, error.code:"VALIDATION_ERROR"}` |
| D10 | CB OPEN 상태에서 외부 의존 엔드포인트 호출 | 503 + `error.code:"SERVICE_UNAVAILABLE"` (CallNotPermittedException 매핑) |
| D11 | 전체 빌드 | `./gradlew build` 성공(4개 서비스 컴파일 + 기존 테스트 통과) |

검증 도구: Testcontainers(Kafka/Redis/PG 기동 후 컨테이너 stop으로 장애 주입), actuator health/circuitbreakers 엔드포인트, Kafka 콘솔 컨슈머로 `.DLT` 토픽 확인.

---

## 7. 구현 파일 목록

### 신규 파일
| 경로 | 내용 |
|------|------|
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/config/KafkaErrorHandlingConfig.kt` | DefaultErrorHandler + DeadLetterPublishingRecoverer (`market.tick.DLT`) |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/config/KafkaErrorHandlingConfig.kt` | DefaultErrorHandler + DLT (`order.events.DLT`) |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/kafka/OrderEventPublisher.kt` | `@CircuitBreaker`+`@Retry` 적용 발행 컴포넌트(self-invocation 회피) |
| `services/market-collector/src/main/kotlin/com/stockpulse/market/controller/GlobalExceptionHandler.kt` | `@RestControllerAdvice` |
| `services/ranking-service/src/main/kotlin/com/stockpulse/ranking/controller/GlobalExceptionHandler.kt` | `@RestControllerAdvice` |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/controller/GlobalExceptionHandler.kt` | `@RestControllerAdvice` |
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/controller/GlobalExceptionHandler.kt` | `@RestControllerAdvice` (controller 존재 시) |
| `services/ranking-service/src/main/kotlin/com/stockpulse/ranking/repository/RankingQueryRepository.kt` (또는 기존 repo 확장) | `findTopReturnRates(size)` DB fallback 쿼리 |

### 수정 파일
| 경로 | 변경 |
|------|------|
| `gradle/libs.versions.toml` | resilience4j 버전·라이브러리·aop·actuator 항목 추가(§5-1) |
| `services/market-collector/build.gradle.kts` | resilience4j + kotlin + aop + actuator 의존성 추가 |
| `services/notification-service/build.gradle.kts` | resilience4j + aop + actuator 추가 |
| `services/trading-service/build.gradle.kts` | resilience4j + aop + actuator 추가 |
| `services/ranking-service/build.gradle.kts` | resilience4j + aop + actuator 추가 |
| `services/market-collector/.../kis/KisApprovalClient.kt` | 레지스트리 주입 + getApprovalKey 코루틴 데코레이트(본문은 private 추출) |
| `services/market-collector/.../service/TickService.kt` | `getQuote`에 `@CircuitBreaker(redis, fallback)` + `getQuoteFromDb` 추가 |
| `services/market-collector/.../repository/TickRepository.kt` | `findLatestBySymbol`/`findFirstBySymbolOrderByTsDesc` 추가 |
| `services/notification-service/.../kafka/TickConsumer.kt` | try/catch 제거(예외를 ErrorHandler로 전파) |
| `services/notification-service/.../service/AlertEvaluator.kt` | `evaluate`에 `@Retry(alertEval)` 추가 |
| `services/trading-service/.../service/TradingService.kt` | `publishOrderEvent` → `OrderEventPublisher.publish` 위임으로 교체 |
| `services/ranking-service/.../service/RankingService.kt` | `getTopRanking`에 `@CircuitBreaker(redis, fallback)` + `getTopRankingFromDb` 추가 |
| 각 서비스 `application.yml` | §1·§3 resilience4j 설정 블록 추가 |

> 미확인 전제(구현 시 검증 필요): ① market-collector `TickService.getQuote`가 Redis 캐시 조회인지·TimescaleDB `ticks` 테이블/엔티티 존재 여부, ② ranking-service의 DB 보유 여부 및 fallback 소스(`ranking_snapshot` 신설 vs account 조회), ③ notification-service에 REST controller 존재 여부(없으면 해당 GlobalExceptionHandler 생략). 이 3건은 backend-dev 구현 착수 시 실제 파일로 확정한다.
