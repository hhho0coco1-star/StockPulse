# Phase 3 — 모의투자 Saga 아키텍트 스펙

## 서비스 구성

| 서비스 | 포트 | DB | 역할 |
|--------|------|----|------|
| trading-service | :8086 | trading | Saga 오케스트레이터, 주문 상태 머신 |
| account-service | :8087 | account | 가상 잔고 예약/확정/해제 |
| portfolio-service | :8088 | portfolio | 보유종목 수량·평가손익 |
| ranking-service | :8089 | Redis | 수익률 랭킹 Sorted Set |

## Kafka 토픽

| 토픽 | 발행자 | 소비자 | 이벤트 타입 |
|------|--------|--------|-------------|
| `order.events` | Trading | Account, Portfolio | RESERVE_BALANCE, UPDATE_HOLDINGS, RELEASE_BALANCE |
| `account.events` | Account | Trading | BALANCE_RESERVED, BALANCE_REJECTED, BALANCE_RELEASED |
| `portfolio.events` | Portfolio | Trading, Ranking | HOLDINGS_UPDATED, HOLDINGS_FAILED |

## Saga 상태 머신

```
주문 접수 → RECEIVED
잔고 예약 성공 → RESERVED
체결 처리 → EXECUTED
보유 갱신 성공 → COMPLETED  ← 정상 종료

잔고 부족 → REJECTED        ← 거절 종료
보유 갱신 실패 → COMPENSATING → CANCELLED  ← 보상 종료
```

## PostgreSQL 스키마

### account DB
```sql
CREATE TABLE account (user_id BIGINT PRIMARY KEY, cash NUMERIC(15,2) DEFAULT 10000000, reserved NUMERIC(15,2) DEFAULT 0, updated_at TIMESTAMPTZ);
CREATE TABLE ledger (id BIGSERIAL PRIMARY KEY, user_id BIGINT, order_id BIGINT, type VARCHAR(20), amount NUMERIC(15,2), balance_after NUMERIC(15,2), created_at TIMESTAMPTZ);
```

### portfolio DB
```sql
CREATE TABLE holdings (id BIGSERIAL PRIMARY KEY, user_id BIGINT, symbol VARCHAR(20), quantity BIGINT, avg_buy_price NUMERIC(12,2), updated_at TIMESTAMPTZ, UNIQUE(user_id, symbol));
```

### trading DB
```sql
CREATE TABLE orders (id BIGSERIAL PRIMARY KEY, user_id BIGINT, symbol VARCHAR(20), side VARCHAR(4), type VARCHAR(6), quantity BIGINT, price NUMERIC(12,2), status VARCHAR(20), filled_price NUMERIC(12,2), created_at TIMESTAMPTZ);
CREATE TABLE order_saga (order_id BIGINT PRIMARY KEY, current_step VARCHAR(30), step_status VARCHAR(10), compensation_reason TEXT, updated_at TIMESTAMPTZ);
CREATE TABLE processed_event (event_id VARCHAR(100) PRIMARY KEY, processed_at TIMESTAMPTZ);
```

## Redis 키 (Ranking)
- `ranking:daily` — Sorted Set, member=userId, score=일간수익률
- `ranking:all` — Sorted Set, member=userId, score=전체수익률

## DoD
| # | 항목 |
|---|------|
| D1 | `./gradlew build` 성공 |
| D2 | POST /orders → 202 응답 + DB 저장 |
| D3 | 잔고 예약 → 보유 갱신 → COMPLETED Saga 정상 흐름 |
| D4 | 잔고 부족 시 REJECTED |
| D5 | GET /portfolio 보유 종목 조회 |
| D6 | GET /ranking 랭킹 응답 |
