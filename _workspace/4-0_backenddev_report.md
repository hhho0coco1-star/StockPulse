# Phase 4 Backend-Dev 구현 보고서

## 1. 구현한 파일 목록

### 루트/공통 수정
- `settings.gradle.kts` — discussion-service, watchlist-service, notification-service 3개 모듈 include 추가
- `init/postgres/01-create-databases.sql` — `CREATE DATABASE discussion; CREATE DATABASE watchlist;` 추가

### discussion-service (:8091)
- `services/discussion-service/build.gradle.kts`
- `services/discussion-service/src/main/resources/application.yml`
- `src/main/kotlin/com/stockpulse/discussion/DiscussionApplication.kt`
- `.../discussion/domain/Post.kt` — `Post`, `Comment`, `PostLike`, `PostLikeId` 엔티티
- `.../discussion/repository/DiscussionRepository.kt` — `PostRepository`, `CommentRepository`, `PostLikeRepository`
- `.../discussion/dto/DiscussionDtos.kt` — 요청/응답 DTO + `ChatBroadcast`
- `.../discussion/service/DiscussionService.kt` — D1~D8 비즈니스 로직
- `.../discussion/controller/DiscussionController.kt` — D1~D8 REST 엔드포인트
- `.../discussion/config/WebSocketConfig.kt` — STOMP `/ws`, `/topic`, `/app`
- `.../discussion/config/RedisConfig.kt` — `StringRedisTemplate`, `RedisMessageListenerContainer`, `PatternTopic("chat:*")`
- `.../discussion/chat/ChatController.kt` — `@MessageMapping("/chat/{roomId}")` → Redis publish
- `.../discussion/chat/ChatRelayListener.kt` — `MessageListener` → `SimpMessagingTemplate` fan-out

### watchlist-service (:8092)
- `services/watchlist-service/build.gradle.kts`
- `services/watchlist-service/src/main/resources/application.yml`
- `src/main/kotlin/com/stockpulse/watchlist/WatchlistApplication.kt`
- `.../watchlist/domain/WatchlistItem.kt` — unique(user_id, symbol) 제약
- `.../watchlist/repository/WatchlistRepository.kt`
- `.../watchlist/dto/WatchlistDtos.kt`
- `.../watchlist/service/WatchlistService.kt` — W1~W3 + `DuplicateSymbolException`
- `.../watchlist/controller/WatchlistController.kt` — W1~W3 REST 엔드포인트

### notification-service (:8093)
- `services/notification-service/build.gradle.kts`
- `services/notification-service/src/main/resources/application.yml`
- `src/main/kotlin/com/stockpulse/notification/NotificationApplication.kt`
- `.../notification/domain/AlertRule.kt` — `AlertRule`, `DeviceToken`, `NotificationLog` (jsonb 매핑)
- `.../notification/repository/NotificationRepository.kt` — 3개 레포지토리
- `.../notification/dto/NotificationDtos.kt`
- `.../notification/service/AlertService.kt` — N1~N7 비즈니스 로직
- `.../notification/service/AlertEvaluator.kt` — 틱 평가 → 이력 저장 → FCM 스텁
- `.../notification/service/FcmStub.kt` — `log.info("FCM 발송: ...")` 스텁
- `.../notification/kafka/TickConsumer.kt` — `@KafkaListener(topics=["market.tick"])`
- `.../notification/controller/NotificationController.kt` — N1~N7 REST 엔드포인트

---

## 2. 스펙 대비 변경/조정 사항

### discussion-service
- **ChatRelayListener**: 스펙은 `RedisMessageListenerContainer` + `PatternTopic("chat:*")` 구독을 지정했고, `MessageListenerAdapter`를 경유해 `onMessage()` 메서드를 바인딩했다. Spring 표준 `MessageListener` 인터페이스를 직접 구현해 타입 안전성을 확보했다.
- **STOMP userId 추출**: 스펙 §3.4에서 "세션 attribute에서 추출, 없으면 0"을 따랐다. Phase 4 범위에서 STOMP 인증 handshake는 구현하지 않았으므로 `headerAccessor.sessionAttributes?.get("userId") as? Long ?: 0L`로 처리한다.
- **채팅 메시지 영속화**: 스펙 §2.1대로 채팅 메시지는 영속화하지 않고 Redis Pub/Sub fan-out만 수행한다.

### watchlist-service
- **DataIntegrityViolationException 처리**: unique(user_id, symbol) 위반 시 JPA/PostgreSQL이 `DataIntegrityViolationException`을 던지므로 이를 `DuplicateSymbolException`으로 변환해 409 CONFLICT를 반환한다.

### notification-service
- **`read` 컬럼 매핑**: `NotificationLog.isRead` 필드에 `@Column(name = "read")` 명시. Kotlin `isXxx` → Java `getXxx()`/`isXxx()` 관례로 컬럼명이 자동으로 `is_read`가 될 수 있어 명시적 컬럼명 지정이 필수.
- **jsonb 매핑**: `@JdbcTypeCode(SqlTypes.JSON)` + `String` 타입으로 저장하고, 응답 직렬화 시 `ObjectMapper.readValue<Map<String,Any>>(json)`으로 파싱한다. 스펙과 동일한 방식.
- **AlertService.toResponse() 접근 범위**: `AlertEvaluator`가 `NotificationLog.toResponse()`를 직접 호출하지 않도록 분리했다(알림 이력 조회는 AlertService에서만 노출).
- **NEWS 규칙**: 스펙대로 Phase 4에서 평가 스킵, 로그만 남긴다.
- **중복 알림 방지**: 스펙에서 Phase 4 범위 외라고 명시했으므로 미구현(틱마다 조건 충족 시 매번 발송).

---

## 3. 빌드 전 확인 필요 사항

### (A) Hibernate 6 / Spring Boot 3.x JSONB 호환성
`@JdbcTypeCode(SqlTypes.JSON)` + `String` 방식은 Hibernate 6.x에서 지원된다. `libs.versions.toml`의 `springBoot = "3.3.5"` 기준 Hibernate 6.5.x가 포함되어 있으므로 정상 동작해야 하나, 첫 기동 시 `ddl-auto: update`가 `jsonb` 컬럼을 `text`로 생성하는 경우가 있다. 이 경우 수동으로 컬럼 타입을 `jsonb`로 ALTER하거나 `ddl-auto: create`로 전환해 재기동해야 한다.

### (B) `@Column(name = "read")` 예약어
`read`는 일부 SQL 방언에서 예약어다. `@Column(name = "read")` 명시로 처리했으나, Hibernate가 `"read"`(따옴표 포함)로 DDL을 생성할 수 있다. 문제 발생 시 컬럼명을 `is_read`로 변경하고 스펙 응답 DTO의 `read` 필드는 `@JsonProperty("read")`로 노출한다.

### (C) PostgreSQL discussion / watchlist DB
`init/postgres/01-create-databases.sql`에 추가했으나, 이미 실행 중인 컨테이너에는 적용되지 않는다. 컨테이너 재생성(`docker compose down -v && docker compose up -d`) 또는 수동으로 `psql -U stockpulse -c "CREATE DATABASE discussion; CREATE DATABASE watchlist;"` 실행이 필요하다.

### (D) discussion-service Redis 의존성
서비스 기동 시 Redis 연결이 없으면 `RedisMessageListenerContainer` Bean 생성 시점에 예외가 발생한다. Redis가 기동 중인지 먼저 확인해야 한다.

### (E) notification-service Kafka 의존성
`@KafkaListener`는 Kafka 브로커 연결이 없어도 애플리케이션은 기동되나, consumer group 등록이 실패한다. `auto-offset-reset: earliest`로 설정되어 있어 Kafka 기동 후 재기동 없이 토픽 메시지를 소비할 수 있다.

### (F) 빌드 명령
```bash
./gradlew :services:discussion-service:build \
          :services:watchlist-service:build \
          :services:notification-service:build
```
