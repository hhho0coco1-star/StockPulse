# StockPulse API 설계

> 모든 외부 요청은 **API Gateway(:8080)** 를 통해 들어온다. 클라이언트(React 웹·RN 앱)는 이 문서의 계약(contract)만 보고 작업한다. DB 스키마는 [04_DB설계.md](04_DB설계.md) 참고.

---

## 1. 공통 규약

### 1.1 기본
- **Base URL**: `https://{host}/api/v1`
- **인증 헤더**: `Authorization: Bearer {accessToken}`
  - Gateway가 JWT를 검증하고, 다운스트림 서비스로 `X-User-Id` 헤더를 전달한다(각 서비스는 JWT를 직접 검증하지 않음 — SafeAlert 패턴 재사용).
- **Content-Type**: `application/json; charset=utf-8`

### 1.2 공통 응답 포맷
```json
{ "success": true, "data": { }, "error": null }
```
실패 시:
```json
{ "success": false, "data": null, "error": { "code": "ORDER_INSUFFICIENT_BALANCE", "message": "가상 잔고가 부족합니다." } }
```

### 1.3 공통 에러 코드
| HTTP | code | 의미 |
|------|------|------|
| 400 | `VALIDATION_ERROR` | 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 토큰 없음/만료 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 중복/상태 충돌 |
| 429 | `RATE_LIMITED` | Rate Limit 초과 (Gateway) |
| 503 | `UPSTREAM_UNAVAILABLE` | 외부 API/서비스 장애 (Circuit Breaker open) |

### 1.4 페이지네이션
- 요청: `?page=0&size=20&sort=createdAt,desc`
- 응답 `data`: `{ "content": [...], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }`

---

## 2. 서비스별 REST 엔드포인트

### 2.1 Auth/User (:8081)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/login` | 로그인 → Access/Refresh 토큰 |
| POST | `/auth/refresh` | Access 토큰 재발급 |
| POST | `/auth/logout` | 로그아웃 (Refresh 무효화) |
| GET | `/auth/oauth2/{provider}` | 소셜 로그인 (google/kakao) 리다이렉트 |
| GET | `/users/me` | 내 프로필 |
| GET | `/watchlist` | 워치리스트 조회 |
| POST | `/watchlist` | 관심종목 추가 `{ "symbol": "005930" }` |
| DELETE | `/watchlist/{symbol}` | 관심종목 삭제 |

### 2.2 Market (:8082)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/market/symbols?market=KR\|US&query=` | 종목 검색 |
| GET | `/market/quote/{symbol}` | 현재가 1건 |
| GET | `/market/quotes?symbols=005930,000660` | 현재가 다건 |
| GET | `/market/candles/{symbol}?interval=1m\|5m\|1d&from=&to=` | 캔들(시계열, TimescaleDB) |

### 2.3 Insight (:8085)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/insights/{symbol}` | 종합 전망 카드 (점수·등급) |
| GET | `/insights/{symbol}/factors` | 근거 상세 (모멘텀·실적·뉴스 분해) |
| GET | `/insights/strong?market=KR&page=` | 강세 종목 리스트 (점수 정렬) |

### 2.4 Trading (:8086) — 모의 주문
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/orders` | 주문 `{ "symbol","side":"BUY\|SELL","type":"MARKET\|LIMIT","quantity","price"? }` |
| GET | `/orders?status=&page=` | 주문 내역 |
| GET | `/orders/{orderId}` | 주문 상세 (Saga 진행 상태 포함) |
| DELETE | `/orders/{orderId}` | 미체결 주문 취소 |

> 주문 접수는 동기 202 Accepted(주문ID 반환), 체결은 Saga 비동기 진행 → 결과는 WebSocket `/user/queue/orders`로 통지.

### 2.5 Account (:8087)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/account` | 가상 잔고 `{ cash, reserved, total }` |
| POST | `/account/reset` | 모의 초기자금 리셋 |

### 2.6 Portfolio (:8088)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/portfolio` | 보유종목 목록 + 평가금액·평가손익 |
| GET | `/portfolio/summary` | 총평가·총수익률 |

### 2.7 Ranking (:8089)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/ranking?period=daily\|weekly\|all&page=` | 수익률 랭킹 |
| GET | `/ranking/me` | 내 순위 |

### 2.8 Community (:8091)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/community/{symbol}/posts?page=` | 종목 토론방 게시글 |
| POST | `/community/{symbol}/posts` | 게시글 작성 `{ "content" }` |
| GET | `/community/posts/{postId}` | 게시글 상세 |
| DELETE | `/community/posts/{postId}` | 게시글 삭제 |
| GET | `/community/posts/{postId}/comments` | 댓글 목록 |
| POST | `/community/posts/{postId}/comments` | 댓글 작성 |
| POST | `/community/posts/{postId}/like` | 좋아요 토글 |

### 2.9 Notification/Alert (:8092)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/alerts` | 알림 규칙 목록 |
| POST | `/alerts` | 규칙 생성 `{ "symbol","type":"TARGET_PRICE\|CHANGE_RATE\|NEWS","condition":{...} }` |
| PUT | `/alerts/{id}` | 규칙 수정 |
| DELETE | `/alerts/{id}` | 규칙 삭제 |
| POST | `/devices` | FCM 디바이스 토큰 등록 `{ "token","platform":"ios\|android\|web" }` |
| DELETE | `/devices/{token}` | 토큰 해제 |
| GET | `/notifications?page=` | 받은 알림 이력 |

---

## 3. WebSocket 채널 (Realtime Gateway :8090)

- **연결**: `wss://{host}/ws` (STOMP over SockJS). CONNECT 프레임 헤더에 `Authorization: Bearer {accessToken}`.
- **구독(subscribe)**

| Destination | 설명 |
|-------------|------|
| `/topic/market/{symbol}` | 실시간 시세 틱 |
| `/topic/insight/{symbol}` | 인사이트 전망 카드 갱신 |
| `/topic/chat/{symbol}` | 종목 채팅 메시지 브로드캐스트 |
| `/user/queue/orders` | 본인 주문 체결/실패 결과 (Saga 완료 통지) |

- **발행(send)**

| Destination | 페이로드 | 설명 |
|-------------|----------|------|
| `/app/chat/{symbol}` | `{ "message": "..." }` | 채팅 전송 → `/topic/chat/{symbol}`로 fan-out |

> 다중 Realtime Gateway 인스턴스 간 메시지 전파는 **Redis Pub/Sub**로 처리한다(2장 데이터 흐름 ④).

---

## 4. FCM 푸시 페이로드

알림 규칙 충족 시 Notification/Alert 서비스가 발송. 공통 형식:
```json
{
  "notification": { "title": "삼성전자 목표가 도달", "body": "70,000원 도달 (현재 70,150원)" },
  "data": {
    "type": "TARGET_PRICE",
    "symbol": "005930",
    "price": "70150",
    "deeplink": "stockpulse://stock/005930"
  }
}
```

| type | 트리거 | data 주요 필드 |
|------|--------|---------------|
| `TARGET_PRICE` | 목표가 도달 | `symbol, price` |
| `CHANGE_RATE` | 급등락(±N%) | `symbol, changeRate` |
| `NEWS` | 워치리스트 종목 뉴스 호재 | `symbol, newsId, headline` |

> `deeplink`로 앱에서 해당 종목 화면으로 바로 이동.
