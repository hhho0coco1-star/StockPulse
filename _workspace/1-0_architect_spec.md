# Phase 1 구현 스펙: 시세 파이프라인

> 근거: docs/02_시스템아키텍처.md §3, docs/03_API설계.md §2.2·§3, docs/04_DB설계.md §3·§5

---

## 1. 구현 범위

| 서비스 | 포트 | 구현 내용 |
|--------|------|----------|
| Market Collector | 8082 | KIS WS 수신 + Kafka 발행 + TimescaleDB 적재 + Redis 캐시 + REST API |
| Realtime Gateway | 8090 | market.tick 소비 + WebSocket STOMP 푸시 + Redis Pub/Sub |
| client/web | - | React + Vite + TradingView Lightweight Charts |

---

## 2. KIS WebSocket 연동 프로토콜

### 2.1 Approval Key 발급
```
POST https://openapivts.koreainvestment.com:29443/oauth2/Approval
Body: { "grant_type": "client_credentials", "appkey": "...", "secretkey": "..." }
응답: { "approval_key": "..." }
```

### 2.2 WebSocket 연결
```
wss://openapivts.koreainvestment.com:29443/websocket
```

### 2.3 구독 메시지 (국내 실시간 체결가: H0STCNT0)
```json
{
  "header": { "approval_key": "{key}", "custtype": "P", "tr_type": "1", "content-type": "utf-8" },
  "body": { "input": { "tr_id": "H0STCNT0", "tr_key": "005930" } }
}
```

### 2.4 수신 데이터 파싱
- 구분자 `^` 로 분리된 문자열
- 인덱스 0: 종목코드, 2: 현재가, 12: 누적거래량

---

## 3. 의존성 추가 (libs.versions.toml)

```toml
[versions]
springKafka = "3.2.4"

[libraries]
# Market Collector
spring-boot-starter-webflux   = { module = "org.springframework.boot:spring-boot-starter-webflux" }
spring-kafka                  = { module = "org.springframework.kafka:spring-kafka" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis" }
# (data-jpa + postgresql 이미 있음)

# Realtime Gateway  
spring-boot-starter-websocket = { module = "org.springframework.boot:spring-boot-starter-websocket" }
# (spring-kafka, spring-boot-starter-data-redis 재사용)

# Coroutines
kotlinx-coroutines-core    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",    version = "1.8.1" }
kotlinx-coroutines-reactor = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-reactor", version = "1.8.1" }
```

---

## 4. 디렉토리 구조

```
services/
├── market-collector/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/stockpulse/market/
│       ├── MarketCollectorApplication.kt
│       ├── config/
│       │   ├── KafkaConfig.kt
│       │   └── RedisConfig.kt
│       ├── kis/
│       │   ├── KisWebSocketClient.kt      ← Coroutines WebSocket
│       │   └── KisApprovalClient.kt       ← Approval Key 발급
│       ├── domain/
│       │   └── Tick.kt                    ← JPA 엔티티
│       ├── repository/
│       │   └── TickRepository.kt
│       ├── service/
│       │   └── TickService.kt             ← DB + Kafka + Redis
│       ├── kafka/
│       │   └── TickProducer.kt
│       └── controller/
│           └── MarketController.kt        ← REST API
├── realtime-gateway/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/stockpulse/realtime/
│       ├── RealtimeGatewayApplication.kt
│       ├── config/
│       │   ├── WebSocketConfig.kt
│       │   └── RedisConfig.kt
│       ├── kafka/
│       │   └── TickConsumer.kt
│       └── handler/
│           └── MarketWebSocketHandler.kt
client/
└── web/
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── main.tsx
        ├── App.tsx
        └── components/
            └── StockChart.tsx
```

---

## 5. TimescaleDB DDL

```sql
-- init/timescaledb/02-market-schema.sql
CREATE TABLE IF NOT EXISTS ticks (
    time   TIMESTAMPTZ NOT NULL,
    symbol VARCHAR(20)  NOT NULL,
    price  NUMERIC(12,2) NOT NULL,
    volume BIGINT        NOT NULL
);
SELECT create_hypertable('ticks', 'time', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_ticks_symbol_time ON ticks (symbol, time DESC);

CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1m
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', time) AS bucket,
    symbol,
    FIRST(price, time)  AS open,
    MAX(price)          AS high,
    MIN(price)          AS low,
    LAST(price, time)   AS close,
    SUM(volume)         AS volume
FROM ticks
GROUP BY bucket, symbol
WITH NO DATA;
```

---

## 6. REST API 계약 (docs/03_API설계.md §2.2 그대로)

| 메서드 | 경로 | 응답 |
|--------|------|------|
| GET | `/market/quote/{symbol}` | `{ symbol, price, change, volume, updatedAt }` |
| GET | `/market/quotes?symbols=` | 위 항목 배열 |
| GET | `/market/candles/{symbol}?interval=1m&from=&to=` | `[{ bucket, open, high, low, close, volume }]` |
| GET | `/market/symbols?market=KR&query=` | 종목 검색 결과 |

---

## 7. WebSocket 채널 (docs/03_API설계.md §3)

- 연결: `ws://localhost:8090/ws` (STOMP)
- 구독: `/topic/market/{symbol}` → `{ symbol, price, volume, time }` 페이로드

---

## 8. Redis 키 설계 (docs/04_DB설계.md §5)

| 키 | 타입 | 내용 |
|----|------|------|
| `quote:{symbol}` | Hash | price, change, volume, updatedAt |

---

## 9. DoD (검증 기준)

| # | 항목 | 방법 |
|---|------|------|
| D1 | `./gradlew build` 성공 | 빌드 실행 |
| D2 | market-collector :8082 기동 | bootRun |
| D3 | realtime-gateway :8090 기동 | bootRun |
| D4 | KIS Approval Key 발급 API 호출 성공 | 로그 확인 |
| D5 | `/market/quote/{symbol}` 응답 | curl |
| D6 | WebSocket `/topic/market/005930` 구독 후 메시지 수신 | wscat 또는 HTML 테스트 |
| D7 | TimescaleDB ticks 테이블 + hypertable 생성 | Docker 기동 후 psql |
| D8 | Redis `quote:005930` Hash 저장 | redis-cli |
