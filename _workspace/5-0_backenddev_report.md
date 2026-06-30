# Phase 5 안정성 구현 — backend-dev 보고서

## 1. 구현 파일 목록

### 신규 파일 (10개)

| 경로 | 내용 |
|------|------|
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/config/KafkaErrorHandlingConfig.kt` | DefaultErrorHandler + DeadLetterPublishingRecoverer → `market.tick.DLT` 라우팅 |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/config/KafkaErrorHandlingConfig.kt` | DefaultErrorHandler + DLT → `order.events.DLT` 라우팅 |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/kafka/OrderEventPublisher.kt` | `@CircuitBreaker(orderEvents)` + `@Retry(orderEvents)` 적용, self-invocation 회피용 분리 컴포넌트 |
| `services/market-collector/src/main/kotlin/com/stockpulse/market/controller/GlobalExceptionHandler.kt` | `@RestControllerAdvice` — 5종 예외 → 표준 ApiResponse 매핑 |
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/controller/GlobalExceptionHandler.kt` | 동일 |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/controller/GlobalExceptionHandler.kt` | 동일 |
| `services/ranking-service/src/main/kotlin/com/stockpulse/ranking/controller/GlobalExceptionHandler.kt` | 동일 |
| `services/discussion-service/src/main/kotlin/com/stockpulse/discussion/controller/GlobalExceptionHandler.kt` | 동일 |
| `services/watchlist-service/src/main/kotlin/com/stockpulse/watchlist/controller/GlobalExceptionHandler.kt` | 동일 |
| `services/ranking-service/src/main/kotlin/com/stockpulse/ranking/repository/RankingQueryRepository.kt` | Redis fallback용 ranking_snapshot 조회 레포지토리 (스텁, Phase 6에서 DB 연동 교체 예정) |

### 수정 파일 (16개)

| 경로 | 변경 내용 |
|------|-----------|
| `gradle/libs.versions.toml` | `resilience4j = "2.2.0"` 버전 추가; `resilience4j-spring-boot3`, `resilience4j-kotlin`, `spring-boot-starter-aop`, `spring-boot-starter-actuator` 라이브러리 항목 추가 |
| `services/market-collector/build.gradle.kts` | resilience4j-spring-boot3, resilience4j-kotlin, aop, actuator 4개 의존성 추가 |
| `services/notification-service/build.gradle.kts` | resilience4j-spring-boot3, aop, actuator 3개 의존성 추가 |
| `services/trading-service/build.gradle.kts` | resilience4j-spring-boot3, aop, actuator 3개 의존성 추가 |
| `services/ranking-service/build.gradle.kts` | resilience4j-spring-boot3, aop, actuator 3개 의존성 추가 |
| `services/discussion-service/build.gradle.kts` | resilience4j-spring-boot3, aop, actuator 3개 의존성 추가 (GlobalExceptionHandler 컴파일용) |
| `services/watchlist-service/build.gradle.kts` | resilience4j-spring-boot3, aop, actuator 3개 의존성 추가 (GlobalExceptionHandler 컴파일용) |
| `services/market-collector/src/main/kotlin/com/stockpulse/market/kis/KisApprovalClient.kt` | CircuitBreakerRegistry·RetryRegistry·TimeLimiterRegistry 생성자 주입; getApprovalKey 본문을 private requestApprovalKey()로 추출 후 코루틴 데코레이터(executeSuspendFunction) 3중 래핑 |
| `services/market-collector/src/main/kotlin/com/stockpulse/market/service/TickService.kt` | getQuote에 `@CircuitBreaker(name="redis", fallbackMethod="getQuoteFromDb")` 추가; `getQuoteFromDb(symbol, Throwable)` fallback 메서드 추가 |
| `services/market-collector/src/main/kotlin/com/stockpulse/market/repository/TickRepository.kt` | `findLatestBySymbol(symbol)` JDBC 쿼리 메서드 추가 (LIMIT 1, ORDER BY time DESC) |
| `services/market-collector/src/main/resources/application.yml` | kisApproval CB/Retry/TimeLimiter 설정, redis CB 설정, actuator 노출 설정 추가 |
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/kafka/TickConsumer.kt` | try/catch 블록 제거 → 예외를 DefaultErrorHandler로 전파 |
| `services/notification-service/src/main/kotlin/com/stockpulse/notification/service/AlertEvaluator.kt` | `evaluate()`에 `@Retry(name = "alertEval")` 어노테이션 추가 |
| `services/notification-service/src/main/resources/application.yml` | alertEval Retry 설정(500ms, 3회, TransientDataAccessException·QueryTimeoutException 재시도, JsonProcessingException 무시), actuator 노출 설정 추가 |
| `services/trading-service/src/main/kotlin/com/stockpulse/trading/service/TradingService.kt` | KafkaTemplate 의존성 제거 → OrderEventPublisher 주입으로 교체; `publishOrderEvent()` 내부에서 `kafkaTemplate.send()` → `orderEventPublisher.publish()` 위임으로 교체 |
| `services/trading-service/src/main/resources/application.yml` | orderEvents CB(50%/20/20s) + Retry(3회, 1s, x2 backoff) 설정, actuator 노출 추가 |
| `services/ranking-service/src/main/kotlin/com/stockpulse/ranking/service/RankingService.kt` | RankingQueryRepository 주입 추가; `getTopRanking()`에 `@CircuitBreaker(name="redis", fallbackMethod="getTopRankingFromDb")` 추가; fallback 메서드 추가 |
| `services/ranking-service/src/main/resources/application.yml` | redis CB 설정(50%/10/10s, RedisConnectionFailureException 등 기록), actuator 노출 추가 |

---

## 2. 스펙 대비 변경사항 (deviation)

### 2-1. discussion-service / watchlist-service 의존성 추가 (스펙 외 추가)

스펙 §5-2에는 discussion-service와 watchlist-service의 build.gradle.kts 수정이 명시되지 않았으나, GlobalExceptionHandler에서 `CallNotPermittedException`(resilience4j 클래스) import가 필요해 컴파일 오류가 발생한다. 두 서비스에도 `resilience4j-spring-boot3`, `spring-boot-starter-aop`, `spring-boot-starter-actuator`를 추가했다.

### 2-2. RankingQueryRepository 스텁 구현

스펙 §3-2에서 `ranking_snapshot` 테이블을 fallback 소스로 권장했으나, ranking-service에 JPA/JDBC 의존성이 없고 자체 DB가 없는 초기 상태이므로 스텁(`emptyList()` 반환)으로 구현했다. Phase 6에서 ranking-service에 JPA + PostgreSQL 추가 후 실 쿼리로 교체해야 한다.

### 2-3. TickRepository는 JPA 대신 JdbcTemplate 기반

스펙 §3-1에서 JPA `findFirstBySymbolOrderByTsDesc()` 파생 쿼리를 언급했으나, 실제 TickRepository는 JPA가 아닌 JdbcTemplate 구현이다. `findLatestBySymbol()` 메서드를 JdbcTemplate LIMIT 1 쿼리로 추가했다.

---

## 3. 빌드 전 주의사항

### 필수 확인

1. **KafkaTemplate 타입 파라미터**: trading-service의 `KafkaErrorHandlingConfig`가 `KafkaTemplate<String, String>` 빈을 주입받는다. trading-service에 해당 타입의 KafkaTemplate 빈이 자동 등록되어 있는지 확인이 필요하다(기존 `application.yml`의 producer 설정이 StringSerializer이므로 Boot 자동설정 기준 동작해야 함).

2. **notification-service KafkaTemplate 빈**: notification-service는 기존에 producer 설정이 없었다. KafkaErrorHandlingConfig가 `KafkaTemplate<String, String>`을 주입받으므로, `application.yml`에 producer 설정 또는 `@Bean KafkaTemplate` 설정을 추가해야 한다. 최소 설정:
   ```yaml
   spring:
     kafka:
       producer:
         key-serializer: org.apache.kafka.common.serialization.StringSerializer
         value-serializer: org.apache.kafka.common.serialization.StringSerializer
   ```

3. **ranking-service DB fallback 스텁**: Redis CB OPEN 시 `getTopRankingFromDb()`는 빈 목록을 반환한다(Phase 6 이전까지). 운영 환경에서는 ranking_snapshot 테이블 도입 또는 account-service 페인트 클라이언트로 교체해야 한다.

4. **TimeLimiter + 코루틴**: `resilience4j-kotlin`의 `timeLimiter.executeSuspendFunction`은 내부적으로 withTimeout을 사용한다. Spring Boot 자동설정 시 `TimeLimiterRegistry` 빈이 자동 등록되려면 `resilience4j-spring-boot3`가 classpath에 있어야 한다(추가 완료).

5. **Actuator 엔드포인트**: `/actuator/circuitbreakers` 활성화를 위해 `management.health.circuitbreakers.enabled: true`를 각 서비스 application.yml에 추가했다. 이미 security 설정이 있는 서비스는 actuator 경로 인가 규칙을 확인해야 한다.

6. **`@Retry` self-invocation 경고**: `AlertEvaluator.evaluate()`는 `TickConsumer`가 외부에서 호출하므로 AOP 프록시가 정상 동작한다(self-invocation 아님).

---

## 4. 보완 구현 (QA 미해결 2건 대응)

QA Phase 5 검증에서 발견된 2건(요청 본문 검증 미작동, 클라이언트 오류의 500 매핑)을 보완했다.

### 이슈 1: 요청 본문 검증(VALIDATION_ERROR) 트리거 경로 활성화

DTO에 jakarta.validation 애너테이션이 없어 잘못된 입력이 success:true로 통과하던 문제를 해결했다. `spring-boot-starter-validation` 의존성 추가 + 쓰기 DTO 제약 애너테이션 + 핸들러 `@Valid` 적용. 검증 실패 시 `MethodArgumentNotValidException` → 각 서비스 GlobalExceptionHandler가 400 + `VALIDATION_ERROR`로 응답한다.

| 파일 | 변경 |
|------|------|
| `gradle/libs.versions.toml` | `spring-boot-starter-validation` 라이브러리 항목 추가 |
| `services/trading-service/build.gradle.kts` | `spring-boot-starter-validation` 의존성 추가 |
| `services/discussion-service/build.gradle.kts` | 동일 |
| `services/watchlist-service/build.gradle.kts` | 동일 |
| `services/notification-service/build.gradle.kts` | 동일 |
| `services/trading-service/.../controller/TradingController.kt` | `OrderRequest`: symbol/side/type `@field:NotBlank`, quantity `@field:Positive`, price `@field:DecimalMin(0.0, inclusive=false)`; `placeOrder` 핸들러 `@Valid @RequestBody` |
| `services/watchlist-service/.../dto/WatchlistDtos.kt` | `AddWatchRequest.symbol`: `@field:NotBlank` + `@field:Pattern("\\d{6}")` |
| `services/watchlist-service/.../controller/WatchlistController.kt` | `add` 핸들러 `@Valid @RequestBody` |
| `services/discussion-service/.../dto/DiscussionDtos.kt` | `PostRequest.content` `@field:NotBlank @field:Size(max=5000)`, `CommentRequest.content` `@field:NotBlank @field:Size(max=2000)`, `ChatMessageRequest.message` `@field:NotBlank @field:Size(max=2000)` |
| `services/discussion-service/.../controller/DiscussionController.kt` | `createPost`/`createComment`/`publishChat` 핸들러 `@Valid @RequestBody` |
| `services/notification-service/.../dto/NotificationDtos.kt` | `AlertRequest.symbol`/`type` `@field:NotBlank`, `DeviceRequest.token`/`platform` `@field:NotBlank` |
| `services/notification-service/.../controller/NotificationController.kt` | `createRule`/`registerDevice` 핸들러 `@Valid @RequestBody` |

### 이슈 2: 클라이언트 오류가 500으로 매핑되던 문제 → 400 매핑

6개 서비스 GlobalExceptionHandler 모두에 generic Exception 핸들러 위쪽으로 다음 2개 핸들러를 추가했다.
- `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST` (깨진/누락 JSON 본문)
- `MissingRequestHeaderException` → 400 `MISSING_HEADER` (X-User-Id 등 필수 헤더 누락)

| 파일 | 변경 |
|------|------|
| `services/market-collector/.../controller/GlobalExceptionHandler.kt` | 2개 핸들러 + import 추가, 미사용 `MethodValidationException` import 제거 |
| `services/notification-service/.../controller/GlobalExceptionHandler.kt` | 2개 핸들러 + import 추가 |
| `services/trading-service/.../controller/GlobalExceptionHandler.kt` | 동일 |
| `services/ranking-service/.../controller/GlobalExceptionHandler.kt` | 동일 |
| `services/discussion-service/.../controller/GlobalExceptionHandler.kt` | 동일 |
| `services/watchlist-service/.../controller/GlobalExceptionHandler.kt` | 동일 |

> `MethodArgumentNotValidException` 핸들러는 6개 서비스 모두 이미 존재해 추가 작업 없음.

### 보완 빌드 전 주의사항

- 검증 애너테이션은 `@field:` 접두사를 사용해야 Kotlin data class 프로퍼티의 backing field에 적용된다(생성자 파라미터에만 붙으면 동작 안 함).
- watchlist `AddWatchRequest`의 `@Pattern("\\d{6}")`은 6자리 숫자 종목코드만 허용한다. 해외/ETF 등 다른 형식 도입 시 패턴 완화 필요.
- discussion/watchlist/notification 컨트롤러의 기존 `try/catch(IllegalArgumentException) → VALIDATION_ERROR`는 그대로 유지된다(서비스 계층 검증용). `@Valid` 실패는 그보다 앞단에서 `MethodArgumentNotValidException`으로 처리되므로 충돌 없음.
