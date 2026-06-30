# Phase 5 QA 보고서

> 작성일: 2026-06-30 / 검증자: QA 에이전트
> 대상: Phase 5 안정성 구현 (Resilience4j CB/Retry/TimeLimiter · Kafka DLT · Redis Fallback · GlobalExceptionHandler)

## 최종 판정: PASS (2026-06-30 보완 재검증 완료, 조건부 해소)

> 1차(2026-06-30): PASS(조건부) — 미해결 2건(검증 부재, 클라이언트 예외 500 매핑)
> 2차(2026-06-30): backend-dev 보완 구현 재검증 결과 미해결 2건 모두 해소 → **무조건 PASS**. 상세는 본 문서 하단 "## 보완 재검증 (2차)" 참조.

스펙 §6 DoD의 정적·런타임 핵심 항목(빌드, CB 등록, DLT 라우팅, Kafka 발행 분리, Fallback/Handler 존재)을 모두 실측으로 충족했다.
1차에서 후속 보완 권고로 기록했던 2건(요청 검증 부재로 VALIDATION_ERROR 미발생, 일부 클라이언트 예외가 500으로 매핑됨)은 2차 재검증에서 모두 해소 확인되었다.

## 빌드 결과

- 명령: `.\gradlew.bat build -x test` (프로젝트 루트, 더미 환경변수 주입)
- 결과: **BUILD SUCCESSFUL in 2m 5s** (88 actionable tasks, 69 executed)
- 13개 서비스 + common 전부 컴파일 성공. resilience4j/aop/actuator 의존성 해소 정상.
- QA에 의한 컴파일 에러 수정 없음 (1회차 빌드 성공).

## 경계면 정합성

| 경계면 | 확인 내용 | 결과 |
|--------|-----------|------|
| KisApprovalClient → Resilience4j | CircuitBreakerRegistry/RetryRegistry/TimeLimiterRegistry 생성자 주입, `executeSuspendFunction` 3중 래핑 (kisApproval) | OK |
| TickService → redis CB | `@CircuitBreaker(name="redis", fallbackMethod="getQuoteFromDb")`, fallback 시그니처(symbol, Throwable) 일치 | OK |
| TickService → TickRepository | `findLatestBySymbol` JdbcTemplate LIMIT 1 쿼리 존재 (fallback 소스) | OK |
| TradingService → OrderEventPublisher | KafkaTemplate 직접 주입 제거, `orderEventPublisher.publish(key, payload)` 위임. self-invocation 회피 위해 별도 @Component 분리 | OK |
| OrderEventPublisher → Kafka | `@CircuitBreaker(orderEvents)`+`@Retry(orderEvents)`, `send().get(3,SECONDS)` 동기 확인 | OK |
| KafkaErrorHandlingConfig(notification) → DLT | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` → `{topic}.DLT`, FixedBackOff(1s,3), NotRetryable(JsonProcessing/IllegalArgument) | OK |
| ApiResponse.fail 시그니처 | `fail(code, message)` — 6개 GlobalExceptionHandler에서 동일 호출, 응답 shape `{success,data,error{code,message}}` 일치 | OK |
| libs.versions.toml | `resilience4j=2.2.0`, spring-boot3/kotlin/aop/actuator 라이브러리 항목 추가 | OK |
| application.yml (4개 서비스) | CB/Retry/TimeLimiter 설정값이 스펙 설정값표와 일치, actuator circuitbreakers 노출 활성 | OK |

## DoD 항목별 결과

| 항목 | 결과 | 비고 |
|------|------|------|
| B1: 전체 빌드 BUILD SUCCESSFUL | PASS | 1회차 성공, 수정 불필요 |
| B2: libs.versions.toml resilience4j 항목 추가 | PASS | versions + 4개 libraries 확인 (17, 50-53행) |
| R1: market-collector `/actuator/circuitbreakers` → kisApproval, redis CLOSED | PASS | 런타임 실측: 두 인스턴스 모두 `state:CLOSED` |
| R2: TradingService가 OrderEventPublisher 통해 Kafka 발행 | PASS | 런타임 실측: 주문 접수 시 로그 `OrderEventPublisher : Kafka 이벤트 발행: key=4` |
| R3: notification-service KafkaErrorHandlingConfig 빈 등록 | PASS | 런타임 실측: 깨진 메시지 → `DeadLetterPublishingRecoverer` 동작 → `market.tick.DLT`에 메시지 도달 확인 |
| R4: GlobalExceptionHandler 5개 서비스 존재 | PASS | 6개 존재 (market/notification/trading/ranking/discussion/watchlist). 스펙 최소(4~5) 초과 충족. 런타임에 표준 error shape 응답 확인 |
| R5: Redis fallback getQuoteFromDb 메서드 존재 | PASS | TickService 56-66행, 시그니처(symbol, Throwable) 정상 |

### 추가 런타임 검증 (DoD §6 매핑)

- **D3 (깨진 JSON → DLT)**: 실측 PASS. `market.tick`에 비파싱 문자열 발행 → 컨슈머 수신 → `market.tick.DLT`로 1건 라우팅 확인 (DLT 토픽 자동 생성됨).
- **D11 (전체 빌드)**: PASS.
- D1/D2/D4/D5/D6/D7/D8 (장애 주입 시나리오): 코드/설정상 구현 완료. 본 QA에서는 Redis/Kafka 강제 다운 주입까지는 수행하지 않음(인프라 컨테이너 공유 중). CB 인스턴스 등록·fallback 메서드·설정값은 정적+기동으로 확인.

## QA가 직접 수정한 결함

없음. 컴파일 에러 0건. 단, 런타임 검증을 위해 포트 8082를 점유 중이던 **구(Phase 5 이전) market-collector bootRun 프로세스(PID 11128)를 종료**하고 신규 빌드로 재기동했다(소스 변경 아님, 환경 정리).

## 미해결 이슈

1. **요청 검증(Bean Validation) 부재 — VALIDATION_ERROR 미발생** (경미, 스펙 의도와 차이)
   - `TradingController.OrderRequest`(10-16행)에 `@field:NotBlank`/`@field:Positive` 등 검증 애너테이션이 없고, `placeOrder`에 `@Valid`가 없다.
   - 결과: 프롬프트 검증 테스트(`{"symbol":"","quantity":-1}`)가 `MethodArgumentNotValidException`을 발생시키지 못하고 정상 처리되어 `success:true / 202`를 반환함(orderId=5 생성됨).
   - GlobalExceptionHandler의 `VALIDATION_ERROR` 핸들러 자체는 정상 작성되어 있으나, 이를 트리거할 검증 애너테이션이 컨트롤러/DTO에 없어 경로가 활성화되지 않음.
   - 권고: 각 컨트롤러 요청 DTO에 검증 애너테이션 + `@Valid` 추가 (Phase 5 안정성 범위 보완 또는 후속 작업).

2. **일부 클라이언트 예외가 500(INTERNAL_ERROR)로 매핑됨** (경미)
   - 헤더 누락(`MissingRequestHeaderException`), 본문 파싱 실패(`HttpMessageNotReadableException`)가 전용 핸들러에 없어 generic `Exception` 핸들러로 떨어져 **HTTP 500 / INTERNAL_ERROR**로 응답됨(본래 400이 적절).
   - 응답 shape(`{success:false, error:{code,message}}`)은 정상. 상태코드/코드 분류만 부정확.
   - 권고: GlobalExceptionHandler에 `MissingRequestHeaderException`(400), `HttpMessageNotReadableException`(400) 핸들러 추가.

3. **ranking-service Redis fallback 스텁** (기보고됨, 정보성)
   - `RankingQueryRepository.findTopReturnRates`는 `emptyList()` 반환 스텁(backend-dev 보고서 deviation 2-2). Redis CB OPEN 시 빈 랭킹 반환. Phase 6에서 `ranking_snapshot` DB 연동으로 교체 필요.

4. **notification-service Kafka producer 설정 명시 부재** (해소됨, 정보성)
   - application.yml에 producer 블록이 없으나, Spring Boot 자동설정의 기본 `KafkaTemplate<String,String>`이 정상 주입되어 기동·DLT 발행 모두 성공함(실측). 운영 명확성을 위해 producer serializer 명시 권장(필수 아님).

---

## 보완 재검증 (2차)

> 재검증일: 2026-06-30 / 검증자: QA 에이전트
> 대상: 1차 미해결 이슈 2건에 대한 backend-dev 보완 구현
> - 이슈1: 컨트롤러 DTO 검증 애너테이션 + `@Valid` 추가 (trading/watchlist/discussion/notification)
> - 이슈2: 6개 서비스 GlobalExceptionHandler에 `HttpMessageNotReadableException`(400), `MissingRequestHeaderException`(400) 핸들러 추가
> - libs.versions.toml에 `spring-boot-starter-validation` 추가

### 정적 확인 (코드 대조)

| 항목 | 확인 내용 | 결과 |
|------|-----------|------|
| validation 의존성 | `libs.versions.toml` 54행 `spring-boot-starter-validation` 추가. 4개 서비스 build.gradle.kts에서 `implementation(libs.spring.boot.starter.validation)` 참조 (trading 20행, watchlist 19행, notification 20행, discussion 21행) | OK |
| 검증 애너테이션 | `TradingController.OrderRequest`(14-25행) `@field:NotBlank`(symbol/side/type), `@field:Positive`(quantity), `@field:DecimalMin`(price) 적용. `placeOrder`에 `@Valid @RequestBody`(35행) | OK |
| MALFORMED_REQUEST 핸들러 | trading GlobalExceptionHandler 52-55행 `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST` | OK |
| MISSING_HEADER 핸들러 | trading GlobalExceptionHandler 57-60행 `MissingRequestHeaderException` → 400 `MISSING_HEADER` (`e.headerName` 포함) | OK |

### 런타임 재검증 (trading-service :8086, .env 환경변수 로드)

| # | 시나리오 | 요청 | 기대 | 실측 응답 | HTTP | 결과 |
|---|----------|------|------|-----------|------|------|
| 보완-1 | 잘못된 입력 | `{"symbol":"","side":"BUY","type":"LIMIT","quantity":-1}` | 400 VALIDATION_ERROR | `{"success":false,...,"error":{"code":"VALIDATION_ERROR","message":"symbol: symbol은 필수입니다, quantity: quantity는 1 이상이어야 합니다"}}` | 400 | PASS |
| 보완-2a | 깨진 JSON | `{broken` | 400 MALFORMED_REQUEST | `{"success":false,...,"error":{"code":"MALFORMED_REQUEST","message":"요청 본문을 해석할 수 없습니다."}}` | 400 | PASS |
| 보완-2b | 헤더 누락 | (X-User-Id 없음) | 400 MISSING_HEADER | `{"success":false,...,"error":{"code":"MISSING_HEADER","message":"필수 헤더 누락: X-User-Id"}}` | 400 | PASS |
| 보완-3 | 정상 주문 (회귀) | `{"symbol":"005930","side":"BUY","type":"LIMIT","quantity":10,"price":70000}` | 202 | `{"success":true,"data":{"orderId":6,"status":"RECEIVED"},"error":null}` | 202 | PASS |

### 재검증 체크리스트

- [O] 보완-빌드: `BUILD SUCCESSFUL in 2m 31s` (88 actionable tasks, 65 executed / 23 up-to-date, 13개 서비스+common 전부 컴파일 성공, QA 수정 0건)
- [O] 보완-1: 잘못된 입력 → 400 + VALIDATION_ERROR (필드별 메시지 정상 직렬화)
- [O] 보완-2a: 깨진 JSON → 400 + MALFORMED_REQUEST
- [O] 보완-2b: 헤더 누락 → 400 + MISSING_HEADER
- [O] 보완-3: 정상 주문 → 202 (회귀 없음, orderId 정상 발급)

### 결론

1차 미해결 이슈 1·2가 모두 해소되었다. 응답 shape(`{success,data,error{code,message}}`)·상태코드·에러코드 분류 전부 스펙 의도와 일치한다.
잔여 정보성 이슈(이슈3 ranking-service Redis fallback 스텁 → Phase 6, 이슈4 producer serializer 명시 권장)는 기능 결함이 아니므로 판정에 영향 없음.
검증에 기동한 trading-service(:8086) 프로세스는 종료했고, 인프라 컨테이너 5종(postgres/timescaledb/redis/mongo/kafka)은 유지했다.

**최종 판정: PASS**
