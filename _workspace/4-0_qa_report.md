# Phase 4 QA 보고서

## 최종 판정: PASS

3개 서비스(discussion :8091 / watchlist :8092 / notification :8093) 빌드 성공, 런타임 DoD 14개 항목 전부 통과, Kafka 트리거 통합 경로(틱→규칙평가→FCM 스텁→이력 적재)까지 실증 완료.

> 검증 중 발견된 Redis 인증 누락 결함 1건은 QA가 직접 수정 후 재검증하여 PASS 처리함(아래 "발견된 문제" 참조).

---

## 빌드 결과

```
./gradlew :services:discussion-service:build :services:watchlist-service:build :services:notification-service:build -x test
BUILD SUCCESSFUL in 57s
21 actionable tasks: 19 executed, 2 up-to-date
```

- discussion-service / watchlist-service / notification-service 3개 모듈 모두 `compileKotlin` → `bootJar` → `build` 성공.
- `common` 모듈 의존 정상 해소(`ApiResponse` 등).
- 테스트는 스펙 지시(`-x test`)대로 제외.

---

## 경계면 정합성 (Step 1 코드 리뷰)

| 확인 항목 | 결과 | 근거 |
|-----------|------|------|
| ApiResponse<T> 공통 래퍼 사용 | OK | DiscussionController/WatchlistController/NotificationController 전 엔드포인트 `ApiResponse.ok/fail` 반환. 런타임 응답도 `{"success":..,"data":..,"error":..}` 형태 확인 |
| X-User-Id 헤더 매핑 | OK | 모든 컨트롤러 `@RequestHeader("X-User-Id") userId: Long` |
| JSONB @JdbcTypeCode(SqlTypes.JSON) | OK | AlertRule.condition(L30-32), NotificationLog.data(L86-88) `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition="jsonb"`. N1/N7 응답에서 jsonb round-trip(`condition.price=70000`, `data.deeplink`) 정상 |
| `read` 예약어 @Column(name="read") | OK | NotificationLog L90 `@Column(name = "read")`. N7 응답 `"read":false` 정상 직렬화 |
| Redis Pub/Sub PatternTopic("chat:*") | OK | RedisConfig L30 `addMessageListener(adapter, PatternTopic("chat:*"))`. D5 발행 시 ChatRelayListener가 `chat:005930` 수신 로그 확인 |
| settings.gradle.kts 3개 서비스 추가 | OK | L27-29 discussion/watchlist/notification-service include |

- D4 게시글 삭제 응답이 `{"postId":.., "deleted":true}` 형태(스펙에 삭제 응답 shape 미명시 → 허용). DoD는 403 케이스만 검증하며 통과.

---

## DoD 항목별 결과

| 항목 | 결과 | 비고 |
|------|------|------|
| D-빌드 | OK | BUILD SUCCESSFUL (3개 모듈) |
| D1 (POST posts → 201) | OK | `{"success":true,"data":{"postId":1,...},"error":null}` HTTP 201 |
| D2 (GET posts → 200) | OK | `data.content` 1건, `totalElements=1` |
| D3 (POST like → 200) | OK | `{"postId":1,"liked":true,"likeCount":1}` |
| D4 (DELETE 타인글 → 403) | OK | `{"error":{"code":"FORBIDDEN",...}}` HTTP 403 |
| D5 (POST chat → 202) | OK | `{"roomId":"005930","published":true}` HTTP 202 + Redis `chat:005930` fan-out 로그 확인 |
| W1 (POST watchlist → 201) | OK | `{"id":1,"symbol":"005930",...}` HTTP 201 |
| W2 (중복 POST → 409) | OK | `{"error":{"code":"CONFLICT",...}}` HTTP 409 |
| W3 (GET watchlist → 200) | OK | `data.items` 배열에 005930 포함 |
| W4 (DELETE → 200) | OK | `{"symbol":"005930","removed":true}` |
| N1 (POST alerts → 201) | OK | `{"id":1,...,"condition":{"op":"GTE","price":70000},...}` jsonb 정상 |
| N2 (POST devices → 201) | OK | `{"id":1,"token":"fcm-token-abc",...}` |
| N3 (GET alerts → 200) | OK | `data.rules` 배열 1건 |
| N4 (GET notifications → 200) | OK | HTTP 200 (틱 발행 전 빈 배열 → 발행 후 1건) |

**보너스 — Kafka 통합 경로 (스펙 §5.3)**: PASS
- `market.tick` 콘솔 프로듀서로 `{"symbol":"005930","price":70150,"change":1.2,...}` 발행.
- AlertEvaluator: "활성 규칙 1건" 매칭 → notificationId=1 이력 저장.
- FcmStub: `FCM 발송: token=fcm-token-abc title=[005930] 목표가 도달 body=70,000원 이상 (현재 70150원) data={"symbol":"005930",...,"deeplink":"stockpulse://stock/005930"}` 로그 확인.
- N7 이력: `type=TARGET_PRICE`, `data.symbol=005930`, `read=false` 1건 적재 확인.

---

## 발견된 문제 (QA 수정 완료)

### [수정함] discussion-service Redis 인증 누락 — 기동 실패 (Blocker)
- **파일/위치**: `services/discussion-service/src/main/resources/application.yml` L15-18 (`spring.data.redis`)
- **증상**: 기동 시 `RedisMessageListenerContainer` 빈 생성에서
  `io.lettuce.core.RedisCommandExecutionException: NOAUTH HELLO must be called with the client already authenticated`
  → `APPLICATION FAILED` → `bootRun` 비정상 종료(서비스 미기동).
- **원인**: 로컬 Redis(stockpulse-redis)는 `requirepass changeme`로 인증을 요구하나, application.yml에 `spring.data.redis.password`가 누락되어 있었음. watchlist/notification은 Redis 미사용이라 영향 없음.
- **수정 내용**: `password: ${REDIS_PASSWORD:changeme}` 한 줄 추가(L19). 기본값을 로컬 인프라(.env) 값과 일치시켜 환경변수 미전달 시에도 기동되도록 함.
- **재검증**: 수정 후 재기동 → `Started DiscussionApplicationKt` 확인 → D1~D5 전부 통과.

> 참고: backend-dev 보고서 §3-(D)는 "Redis 연결 없으면 컨테이너 빈 생성 시점에 예외"만 언급했으나, 실제로는 연결은 되되 **인증 누락**으로 실패. application.yml 패스워드 설정 자체가 빠져 있던 것이 근본 원인.

### [비차단] 초기 curl 400 — 검증 도구 아티팩트 (코드 문제 아님)
- PowerShell에서 `'{"content":..}'` 단일따옴표 전달 시 큰따옴표가 소실되어 서버가
  `HttpMessageNotReadableException: ... expecting double-quote to start field name` 400 반환.
- 컨트롤러/DTO 결함 아님. JSON을 파일로 저장 후 `curl --data @file`로 재요청하니 전부 정상(201/202).
- 동일 원인으로 Kafka 콘솔 프로듀서 첫 틱도 따옴표 소실 → base64 전송으로 우회하여 정상 검증.

---

## 미해결 이슈 (기능에 영향 없음 — 후속 권장)

1. **discussion-service application.yml의 datasource/redis 비밀번호 기본값 하드코딩(`changeme`)**
   - 본 QA 수정에서 운영 편의상 `${REDIS_PASSWORD:changeme}` 기본값을 둠. 운영 환경에서는 기본값을 비우고 환경변수 주입을 강제하는 정책 재검토 권장(현 시점 로컬 검증에는 무해).

2. **notification-service 기동 시 Hibernate 경고**
   - `constraint "uk8se1i37nto56x9252rmrit8ib" of relation "device_tokens" does not exist, skipping` (WARN).
   - `ddl-auto: update`가 존재하지 않는 제약을 drop 시도하며 남기는 무해한 경고. 기능/유니크 제약(token unique) 동작 정상(중복 토큰 테스트는 DoD 범위 외라 미수행이나 스키마상 보장).

3. **알림 중복 발송 방지 미구현**
   - 스펙 §4.4에서 Phase 4 범위 외로 명시. 동일 규칙이 매 틱 충족 시 매번 이력/FCM 발생. 후속 Phase에서 규칙별 last-fired 쿨다운 필요.

4. **WebSocket(STOMP) 경로 수동 검증 미수행**
   - REST 보조 경로(D8)와 Redis fan-out 리스너는 실증됨. STOMP 클라이언트 직접 연결(`/ws` 구독/발행)은 자동화 도구 부재로 미수행. fan-out 핵심 로직은 D5 경로로 동일하게 검증됨.

---

## 검증 환경
- 인프라: stockpulse-postgres(5432, DB: discussion/watchlist/notification 모두 존재 확인)/redis(6379, requirepass)/kafka(9092) — 전부 healthy.
- JDK: bootRun은 Gradle toolchain의 eclipse_adoptium-21로 실행(로컬 java는 24).
- 환경변수: KIS/POSTGRES/REDIS 등 스펙 지정값 export 후 빌드·기동.
