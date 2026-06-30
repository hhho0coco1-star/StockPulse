# Phase 4 구현 스펙

> 대상: **discussion-service(:8091)**, **watchlist-service(:8092)**, **notification-service(:8093)**
> 기존 패턴 준수: `ApiResponse<T>` 공통 래퍼(`com.stockpulse.common.ApiResponse`), 다운스트림은 JWT 직접 검증 없이 Gateway가 전달하는 **`X-User-Id`** 헤더로 userId 식별, Spring Boot 3.3.5 + Kotlin 1.9.25 + Java 21.
> 저장소: 서비스별 PostgreSQL DB(DB per service). discussion 채팅 fan-out은 Redis Pub/Sub. notification 트리거는 Kafka `market.tick` 소비.

---

## 0. 설계 정합성 메모 (docs 대비 변경점)

기존 docs(02/03/04)는 토론을 **Community(:8091)**, 워치리스트를 **Auth/User(:8081)** 안에, 알림을 **Notification(:8092)** 으로 배치한다. Phase 4 임무는 이를 **독립 마이크로서비스 3개**로 분리하고 포트를 8091/8092/8093으로 재배치한다. 본 스펙은 임무 정의를 정본으로 한다. 구현 완료 후 `docs/02·03·04`의 포트·서비스 표를 동기화할 것(qa 검증 후 architect 후속 작업).

- discussion-service(:8091): docs Community + Realtime Gateway의 채팅 책임을 통합 (게시글 CRUD + STOMP 채팅).
- watchlist-service(:8092): docs Auth/User의 워치리스트 책임을 분리.
- notification-service(:8093): docs Notification(:8092) → :8093 으로 재배치.

공통 규약(03 §1 재사용):
- 성공: `{ "success": true, "data": {...}, "error": null }`
- 실패: `{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }`
- 페이지네이션 `data`: `{ "content": [...], "page", "size", "totalElements", "totalPages" }`
- 공통 에러코드: `VALIDATION_ERROR`(400), `NOT_FOUND`(404), `FORBIDDEN`(403), `CONFLICT`(409).

---

## 1. 서비스별 REST API 계약

> 모든 엔드포인트는 Gateway 경유를 가정하며 `@RequestHeader("X-User-Id") userId: Long` 으로 사용자 식별. 응답 본문은 항상 `ApiResponse<T>` 래퍼. 아래 "Response data"는 래퍼 `data` 필드의 shape.

### 1.1 discussion-service (:8091)

#### 게시글(REST, PostgreSQL)

| # | Method | Path | 설명 | 성공 코드 |
|---|--------|------|------|----------|
| D1 | GET | `/discussions/{symbol}/posts?page=0&size=20` | 종목 토론방 게시글 목록(페이지) | 200 |
| D2 | POST | `/discussions/{symbol}/posts` | 게시글 작성 | 201 |
| D3 | GET | `/discussions/posts/{postId}` | 게시글 상세 | 200 |
| D4 | DELETE | `/discussions/posts/{postId}` | 게시글 삭제(작성자 본인만) | 200 |
| D5 | GET | `/discussions/posts/{postId}/comments?page=0&size=20` | 댓글 목록 | 200 |
| D6 | POST | `/discussions/posts/{postId}/comments` | 댓글 작성 | 201 |
| D7 | POST | `/discussions/posts/{postId}/like` | 좋아요 토글 | 200 |

**D2 Request body**
```json
{ "content": "삼성전자 실적 기대됩니다" }
```
**Post Response data (D2/D3 단건)**
```json
{
  "postId": 12, "symbol": "005930", "userId": 7,
  "content": "삼성전자 실적 기대됩니다",
  "likeCount": 0, "commentCount": 0,
  "createdAt": "2026-06-30T05:21:00Z"
}
```
**D1 Response data** — 페이지 래퍼, `content`는 위 Post shape 배열.

**D6 Request body**
```json
{ "content": "동의합니다" }
```
**Comment Response data (D6 단건 / D5 content 요소)**
```json
{ "commentId": 30, "postId": 12, "userId": 8, "content": "동의합니다", "createdAt": "2026-06-30T05:25:00Z" }
```

**D7 Response data (토글 결과)**
```json
{ "postId": 12, "liked": true, "likeCount": 1 }
```

| 에러 상황 | code | HTTP |
|-----------|------|------|
| content 비어있음/길이 초과 | `VALIDATION_ERROR` | 400 |
| 게시글/댓글 대상 없음 | `NOT_FOUND` | 404 |
| 본인 글이 아닌데 삭제 시도 | `FORBIDDEN` | 403 |

#### 채팅 전송(REST 보조 트리거) — 선택

WebSocket(`/app/chat/{roomId}`)이 정본이나, 테스트 용이성을 위해 동일 fan-out을 일으키는 REST 보조 엔드포인트 1개 제공:

| # | Method | Path | 설명 | 성공 코드 |
|---|--------|------|------|----------|
| D8 | POST | `/discussions/{roomId}/chat` | 채팅 메시지 발행(Redis Pub/Sub → STOMP fan-out) | 202 |

**D8 Request body**: `{ "message": "안녕하세요" }`
**D8 Response data**: `{ "roomId": "005930", "published": true }`

### 1.2 watchlist-service (:8092)

| # | Method | Path | 설명 | 성공 코드 |
|---|--------|------|------|----------|
| W1 | GET | `/watchlist` | 내 워치리스트 종목 목록 | 200 |
| W2 | POST | `/watchlist` | 종목 추가 | 201 |
| W3 | DELETE | `/watchlist/{symbol}` | 종목 삭제 | 200 |

**W2 Request body**: `{ "symbol": "005930" }`
**Watchlist item shape (W1 배열 요소 / W2 단건)**
```json
{ "id": 5, "symbol": "005930", "createdAt": "2026-06-30T05:00:00Z" }
```
**W1 Response data**: `{ "items": [ {item}, ... ] }`
**W3 Response data**: `{ "symbol": "005930", "removed": true }`

| 에러 상황 | code | HTTP |
|-----------|------|------|
| symbol 형식 오류/빈값 | `VALIDATION_ERROR` | 400 |
| 이미 추가된 종목(unique 충돌) | `CONFLICT` | 409 |
| 삭제 대상 종목 없음 | `NOT_FOUND` | 404 |

### 1.3 notification-service (:8093)

| # | Method | Path | 설명 | 성공 코드 |
|---|--------|------|------|----------|
| N1 | GET | `/alerts` | 알림 규칙 목록 | 200 |
| N2 | POST | `/alerts` | 규칙 생성 | 201 |
| N3 | PUT | `/alerts/{id}` | 규칙 수정(조건·enabled) | 200 |
| N4 | DELETE | `/alerts/{id}` | 규칙 삭제 | 200 |
| N5 | POST | `/devices` | FCM 디바이스 토큰 등록 | 201 |
| N6 | DELETE | `/devices/{token}` | 토큰 해제 | 200 |
| N7 | GET | `/notifications?page=0&size=20` | 받은 알림 이력 | 200 |

**N2 Request body**
```json
{ "symbol": "005930", "type": "TARGET_PRICE", "condition": { "op": "GTE", "price": 70000 }, "enabled": true }
```
- `type`: `TARGET_PRICE | CHANGE_RATE | NEWS`
- `condition`(jsonb):
  - `TARGET_PRICE`: `{ "op": "GTE"|"LTE", "price": <number> }`
  - `CHANGE_RATE`: `{ "op": "GTE"|"LTE", "rate": <percent number> }`
  - `NEWS`: `{ "keyword": "호재" }` (Phase 4에서는 평가 스텁)

**Alert rule shape (N1 요소 / N2·N3 단건)**
```json
{ "id": 3, "userId": 7, "symbol": "005930", "type": "TARGET_PRICE",
  "condition": { "op": "GTE", "price": 70000 }, "enabled": true,
  "createdAt": "2026-06-30T05:00:00Z" }
```
**N1 Response data**: `{ "rules": [ {rule}, ... ] }`

**N3 Request body**: `{ "condition": { "op": "GTE", "price": 72000 }, "enabled": false }`

**N5 Request body**: `{ "token": "fcm-token-abc", "platform": "android" }` (`platform`: `ios|android|web`)
**Device shape (N5)**: `{ "id": 2, "userId": 7, "token": "fcm-token-abc", "platform": "android", "createdAt": "..." }`
**N6 Response data**: `{ "token": "fcm-token-abc", "removed": true }`

**Notification history shape (N7 content 요소)**
```json
{ "id": 9, "userId": 7, "type": "TARGET_PRICE",
  "title": "삼성전자 목표가 도달", "body": "70,000원 도달 (현재 70,150원)",
  "data": { "symbol": "005930", "price": "70150", "deeplink": "stockpulse://stock/005930" },
  "read": false, "createdAt": "2026-06-30T05:30:00Z" }
```

| 에러 상황 | code | HTTP |
|-----------|------|------|
| type 미지원/condition 누락 | `VALIDATION_ERROR` | 400 |
| 토큰 중복 등록 | `CONFLICT` | 409 |
| 규칙/토큰 대상 없음 | `NOT_FOUND` | 404 |
| 타인 규칙 수정·삭제 시도 | `FORBIDDEN` | 403 |

---

## 2. DB 스키마 (PostgreSQL DDL)

> `init/postgres/01-create-databases.sql`에 `community`·`notification` DB는 존재하나 **`discussion`·`watchlist` DB는 없음**. 아래 §2.0 추가 DDL 필요. 본 Phase는 서비스명에 맞춰 신규 DB 3개(`discussion`, `watchlist`, `notification`)를 사용한다(notification은 기존 DB 재사용 가능).
> 테이블 DDL은 각 서비스 JPA `ddl-auto: update`로 생성하되, 참조 명세를 아래에 둔다.

### 2.0 데이터베이스 생성 추가 (init/postgres/01-create-databases.sql)

기존 파일 끝에 추가:
```sql
CREATE DATABASE discussion;
CREATE DATABASE watchlist;
-- notification DB는 이미 존재 (기존 6번 라인)
```
> `community` DB는 본 Phase에서 미사용(레거시). 신규 토론 데이터는 `discussion` DB에 적재.

### 2.1 discussion-service (DB: discussion)

```sql
CREATE TABLE posts (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20)  NOT NULL,
    user_id       BIGINT       NOT NULL,
    content       TEXT         NOT NULL,
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_posts_symbol_created ON posts (symbol, created_at DESC);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_post ON comments (post_id, created_at);

CREATE TABLE post_likes (
    post_id    BIGINT      NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
```
> 채팅 메시지는 휘발성(Redis Pub/Sub fan-out)으로 영속화하지 않음. (이력 보존이 필요해지면 후속 Phase에서 chat_messages 테이블 추가.)

### 2.2 watchlist-service (DB: watchlist)

```sql
CREATE TABLE watchlist (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    symbol     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_watchlist_user_symbol UNIQUE (user_id, symbol)
);
CREATE INDEX idx_watchlist_user ON watchlist (user_id);
```

### 2.3 notification-service (DB: notification)

```sql
CREATE TABLE alert_rules (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    symbol     VARCHAR(20)  NOT NULL,
    type       VARCHAR(20)  NOT NULL,            -- TARGET_PRICE | CHANGE_RATE | NEWS
    condition  JSONB        NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_alert_rules_symbol_enabled ON alert_rules (symbol, enabled);
CREATE INDEX idx_alert_rules_user ON alert_rules (user_id);

CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    platform   VARCHAR(10)  NOT NULL,            -- ios | android | web
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    data       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    read       BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
```

> JPA 엔티티에서 `condition`/`data` jsonb 매핑은 `@JdbcTypeCode(SqlTypes.JSON)` + `String`(JSON 문자열) 또는 `Map<String,Any>`로 처리. `read`는 예약어 회피 위해 `@Column(name = "read")` 명시.

---

## 3. WebSocket 채널 (discussion-service)

기존 `realtime-gateway`의 STOMP 패턴(`WebSocketConfig`, `SimpMessagingTemplate`)을 재사용한다.

### 3.1 STOMP 엔드포인트 / 브로커
- 연결 엔드포인트: `/ws` (SockJS, `setAllowedOriginPatterns("*")`)
- 브로커 prefix: `enableSimpleBroker("/topic")`, `setApplicationDestinationPrefixes("/app")`

### 3.2 채널

| 종류 | Destination | 설명 |
|------|-------------|------|
| 구독(subscribe) | `/topic/chat/{roomId}` | 토론방 채팅 브로드캐스트 수신 (`roomId` = 종목코드) |
| 발행(send) | `/app/chat/{roomId}` | 클라이언트 → 서버 채팅 전송 → Redis Pub/Sub로 fan-out |

### 3.3 메시지 포맷

**클라이언트 → 서버 (`/app/chat/{roomId}` 페이로드)**
```json
{ "message": "안녕하세요" }
```
**서버 → 구독자 (`/topic/chat/{roomId}` 브로드캐스트)**
```json
{ "roomId": "005930", "userId": 7, "message": "안녕하세요", "sentAt": "2026-06-30T05:40:00Z" }
```

### 3.4 Redis Pub/Sub fan-out (멀티 인스턴스)

DB설계 §5의 채널 규약을 따른다.

- 채널명: **`chat:{roomId}`** (예: `chat:005930`)
- 흐름:
  1. `@MessageMapping("/chat/{roomId}")` 핸들러(또는 D8 REST)가 메시지 수신.
  2. 핸들러는 직접 STOMP로 보내지 **않고** `RedisTemplate.convertAndSend("chat:$roomId", payloadJson)` 로 발행.
  3. 모든 인스턴스가 `RedisMessageListenerContainer`로 `chat:*` (PatternTopic `chat:*`) 구독.
  4. 메시지 수신 시 각 인스턴스가 `SimpMessagingTemplate.convertAndSend("/topic/chat/$roomId", payload)` → 자신에게 붙은 클라이언트로 fan-out.
- 효과: 어느 인스턴스에 연결된 클라이언트든 동일 메시지 수신(수평 확장·무중단).
- userId 출처: STOMP CONNECT 시 `simpUser`/세션 attribute, 또는 D8 REST의 `X-User-Id`. (Phase 4: STOMP 경로는 세션 attribute에서 추출, 없으면 0.)

---

## 4. Kafka 이벤트 (notification-service)

### 4.1 소비 토픽

| 토픽 | groupId | 파티션 키 | 용도 |
|------|---------|----------|------|
| `market.tick` | `notification-service` | 종목코드 | 시세 틱 수신 → 가격 조건 알림 규칙 평가 |

### 4.2 페이로드 shape (`market.tick` — Market Collector 발행, 기존 realtime-gateway 소비 형태와 동일)
```json
{ "symbol": "005930", "price": 70150, "change": 1.2, "volume": 12345, "ts": "2026-06-30T05:30:00Z" }
```
> 소비는 `objectMapper.readValue(message, Map::class.java)` 후 `symbol`/`price`/`change`(=changeRate) 추출. `price`는 숫자(정수/실수) 가정.

### 4.3 트리거 조건 (규칙 평가)

수신 틱마다:
1. `alert_rules` 에서 `symbol = tick.symbol AND enabled = true` 조회.
2. 각 규칙 `type`별 평가:
   - `TARGET_PRICE`: `op=GTE`이면 `price >= condition.price`, `op=LTE`이면 `price <= condition.price`.
   - `CHANGE_RATE`: `op=GTE`이면 `tick.change >= condition.rate`, `op=LTE`이면 `tick.change <= condition.rate`.
   - `NEWS`: Phase 4 미평가(스킵, 후속 Phase에서 `news.raw`/`insight.updated` 연동).
3. 조건 충족 시:
   - `notifications` 테이블에 이력 insert (title/body/data 구성).
   - 해당 `user_id`의 `device_tokens` 전체 조회 → 토큰별 **FCM 발송 스텁** 호출.

### 4.4 FCM 발송 스텁

실제 Firebase 호출 없이 로그만 남긴다:
```kotlin
log.info("FCM 발송: token={} title={} body={} data={}", token, title, body, dataJson)
```
- FCM 페이로드 형식(참고, 03 §4): `{ "notification": { "title", "body" }, "data": { "type", "symbol", "price", "deeplink" } }`
- `deeplink`: `stockpulse://stock/{symbol}`.

> 중복 알림 방지(같은 규칙 연속 충족 시 매 틱 발송 폭주)는 Phase 4 범위 외(스텁). 필요 시 후속에서 규칙별 last-fired 쿨다운 추가.

---

## 5. DoD — 런타임 검증 항목

> 전제: 인프라(PostgreSQL·Redis·Kafka) 기동, 3개 서비스 부팅 성공(JPA `ddl-auto: update`로 테이블 자동 생성). Gateway 미경유 직접 호출 시 `X-User-Id` 헤더 수동 지정. 응답은 모두 `{"success":true,...}` 래퍼.

### 5.1 discussion-service (:8091)
```bash
# 게시글 작성 (201)
curl -s -X POST localhost:8091/discussions/005930/posts \
  -H "X-User-Id: 7" -H "Content-Type: application/json" \
  -d '{"content":"삼성전자 실적 기대"}'
# 기대: {"success":true,"data":{"postId":1,"symbol":"005930","userId":7,"likeCount":0,...},"error":null}

# 목록 조회 (200, 페이지 래퍼)
curl -s "localhost:8091/discussions/005930/posts?page=0&size=20" -H "X-User-Id: 7"
# 기대: data.content 배열에 위 게시글 포함, data.totalElements >= 1

# 좋아요 토글 (200)
curl -s -X POST localhost:8091/discussions/posts/1/like -H "X-User-Id: 7"
# 기대: data.liked=true, data.likeCount=1

# 댓글 작성 (201)
curl -s -X POST localhost:8091/discussions/posts/1/comments \
  -H "X-User-Id: 8" -H "Content-Type: application/json" -d '{"content":"동의"}'
# 기대: data.commentId 존재, data.postId=1

# 타인 글 삭제 시도 → 403
curl -s -o /dev/null -w "%{http_code}" -X DELETE localhost:8091/discussions/posts/1 -H "X-User-Id: 999"
# 기대: 403

# 채팅 fan-out (202) — Redis 발행 확인
curl -s -X POST localhost:8091/discussions/005930/chat \
  -H "X-User-Id: 7" -H "Content-Type: application/json" -d '{"message":"hi"}'
# 기대: data.published=true  (+ 서버 로그에 chat:005930 발행, /topic/chat/005930 송신)
```
- WebSocket 검증(수동): STOMP 클라이언트로 `/ws` 연결 → `/topic/chat/005930` 구독 → `/app/chat/005930` 로 `{"message":"hi"}` 전송 → 동일 메시지 수신 확인.

### 5.2 watchlist-service (:8092)
```bash
# 추가 (201)
curl -s -X POST localhost:8092/watchlist \
  -H "X-User-Id: 7" -H "Content-Type: application/json" -d '{"symbol":"005930"}'
# 기대: data.symbol="005930", data.id 존재

# 중복 추가 → 409
curl -s -o /dev/null -w "%{http_code}" -X POST localhost:8092/watchlist \
  -H "X-User-Id: 7" -H "Content-Type: application/json" -d '{"symbol":"005930"}'
# 기대: 409

# 조회 (200)
curl -s localhost:8092/watchlist -H "X-User-Id: 7"
# 기대: data.items 배열에 005930 포함

# 삭제 (200)
curl -s -X DELETE localhost:8092/watchlist/005930 -H "X-User-Id: 7"
# 기대: data.removed=true
```

### 5.3 notification-service (:8093)
```bash
# 규칙 생성 (201)
curl -s -X POST localhost:8093/alerts \
  -H "X-User-Id: 7" -H "Content-Type: application/json" \
  -d '{"symbol":"005930","type":"TARGET_PRICE","condition":{"op":"GTE","price":70000},"enabled":true}'
# 기대: data.id 존재, data.condition.price=70000

# 디바이스 토큰 등록 (201)
curl -s -X POST localhost:8093/devices \
  -H "X-User-Id: 7" -H "Content-Type: application/json" \
  -d '{"token":"fcm-token-abc","platform":"android"}'
# 기대: data.token="fcm-token-abc"

# 규칙 목록 (200)
curl -s localhost:8093/alerts -H "X-User-Id: 7"
# 기대: data.rules 배열에 위 규칙 포함

# Kafka 트리거 검증: market.tick 발행 → 조건 충족 → FCM 스텁 로그 + 이력 적재
#   콘솔 프로듀서로 발행:
echo '{"symbol":"005930","price":70150,"change":1.2,"volume":1,"ts":"2026-06-30T05:30:00Z"}' \
  | <kafka-console-producer --topic market.tick ...>
# 기대(로그): "FCM 발송: token=fcm-token-abc title=... body=..."
# 기대(이력):
curl -s "localhost:8093/notifications?page=0&size=20" -H "X-User-Id: 7"
#   → data.content 에 type=TARGET_PRICE, data.symbol=005930 알림 1건 이상
```

### 5.4 빌드
```bash
./gradlew :services:discussion-service:build \
          :services:watchlist-service:build \
          :services:notification-service:build
# 기대: BUILD SUCCESSFUL (3개 모듈 컴파일·테스트 통과)
```

---

## 6. 구현 파일 목록

### 6.1 루트/공통 (수정)
- `settings.gradle.kts` — include에 3개 모듈 추가:
  ```kotlin
  "services:discussion-service",
  "services:watchlist-service",
  "services:notification-service"
  ```
- `init/postgres/01-create-databases.sql` — `CREATE DATABASE discussion; CREATE DATABASE watchlist;` 추가 (§2.0)
- `gradle/libs.versions.toml` — 신규 alias 불필요(기존 `spring-boot-starter-web`, `-data-jpa`, `spring.kafka`, `-data-redis`, `-websocket`, `postgresql`, `jackson-module-kotlin`, `kotlin-reflect`, `-starter-test` 재사용)

### 6.2 discussion-service (port 8091, DB discussion)
- `services/discussion-service/build.gradle.kts` — 의존: common, web, data-jpa, postgresql, spring-kafka(미사용 시 생략), data-redis, websocket, jackson, kotlin-reflect, test
- `services/discussion-service/src/main/resources/application.yml` — port 8091, datasource `jdbc:postgresql://localhost:5432/discussion`, redis host/port, kafka(선택)
- `services/discussion-service/src/main/kotlin/com/stockpulse/discussion/DiscussionApplication.kt`
- `.../discussion/domain/Post.kt` — `Post`, `Comment`, `PostLike` 엔티티
- `.../discussion/repository/DiscussionRepository.kt` — `PostRepository`, `CommentRepository`, `PostLikeRepository`
- `.../discussion/service/DiscussionService.kt` — 게시글/댓글/좋아요 비즈니스 로직(카운트 증감 포함)
- `.../discussion/controller/DiscussionController.kt` — D1~D7 (+ D8 채팅 REST)
- `.../discussion/config/WebSocketConfig.kt` — STOMP `/ws`, broker `/topic`·`/app`
- `.../discussion/config/RedisConfig.kt` — `RedisMessageListenerContainer` + `PatternTopic("chat:*")` 구독 등록
- `.../discussion/chat/ChatController.kt` — `@MessageMapping("/chat/{roomId}")` → Redis publish
- `.../discussion/chat/ChatRelayListener.kt` — Redis 메시지 수신 → `SimpMessagingTemplate` fan-out
- `.../discussion/dto/DiscussionDtos.kt` — `PostRequest`, `CommentRequest`, `ChatMessage` 등

### 6.3 watchlist-service (port 8092, DB watchlist)
- `services/watchlist-service/build.gradle.kts` — 의존: common, web, data-jpa, postgresql, jackson, kotlin-reflect, test
- `services/watchlist-service/src/main/resources/application.yml` — port 8092, datasource `.../watchlist`
- `.../watchlist/WatchlistApplication.kt`
- `.../watchlist/domain/WatchlistItem.kt` — 엔티티(unique user_id+symbol)
- `.../watchlist/repository/WatchlistRepository.kt`
- `.../watchlist/service/WatchlistService.kt` — 추가(중복→CONFLICT)/삭제/조회
- `.../watchlist/controller/WatchlistController.kt` — W1~W3
- `.../watchlist/dto/WatchlistDtos.kt` — `AddWatchRequest`

### 6.4 notification-service (port 8093, DB notification)
- `services/notification-service/build.gradle.kts` — 의존: common, web, data-jpa, postgresql, spring-kafka, jackson, kotlin-reflect, test
- `services/notification-service/src/main/resources/application.yml` — port 8093, datasource `.../notification`, kafka bootstrap + consumer group `notification-service`
- `.../notification/NotificationApplication.kt`
- `.../notification/domain/AlertRule.kt` — `AlertRule`, `DeviceToken`, `NotificationLog` 엔티티(jsonb 매핑)
- `.../notification/repository/NotificationRepository.kt` — 3개 리포지토리(`findBySymbolAndEnabledTrue`, `findByUserId` 등)
- `.../notification/service/AlertService.kt` — 규칙·디바이스 CRUD
- `.../notification/service/AlertEvaluator.kt` — 틱→규칙 평가→이력 적재→FCM 스텁
- `.../notification/service/FcmStub.kt` — `log.info("FCM 발송: ...")`
- `.../notification/kafka/TickConsumer.kt` — `@KafkaListener(topics=["market.tick"], groupId="notification-service")`
- `.../notification/controller/NotificationController.kt` — N1~N7
- `.../notification/dto/NotificationDtos.kt` — `AlertRequest`, `AlertUpdateRequest`, `DeviceRequest`

### 6.5 (구현 후 동기화 — architect 후속)
- `docs/02_시스템아키텍처.md`, `docs/03_API설계.md`, `docs/04_DB설계.md` — Phase 4 서비스 분리·포트(8091/8092/8093) 반영
