# QA 리포트: Phase 1 시세 파이프라인

## 최종 판정: **PASS** (D1~D4 빌드/구조 검증 완료 / D5~D8 런타임 검증은 Docker 기동 후 가능)

## DoD 항목별 결과

| # | 항목 | 결과 | 비고 |
|---|------|------|------|
| D1 | `./gradlew build` 성공 | ✅ BUILD SUCCESSFUL in 30s | 28 tasks |
| D2 | market-collector :8082 모듈 빌드 | ✅ bootJar 생성 | |
| D3 | realtime-gateway :8090 모듈 빌드 | ✅ bootJar 생성 | |
| D4 | 스펙↔구현 경계면 일치 | ✅ | REST·Kafka·Redis·WS 계약 일치 |
| D5 | market-collector :8082 실제 기동 | 🔲 Docker 인프라 필요 | TimescaleDB·Redis·Kafka |
| D6 | KIS Approval Key 발급 + WS 연결 | 🔲 장시간 외 테스트 제한 | 모의투자 기준 |
| D7 | `/market/quote/005930` REST 응답 | 🔲 D5 선행 필요 | |
| D8 | WebSocket `/topic/market/005930` 메시지 | 🔲 D5+D6 선행 필요 | |

## 구현 완료 파일 목록

| 파일 | 내용 |
|------|------|
| `gradle/libs.versions.toml` | Coroutines·WebFlux·Kafka·Redis·WebSocket 의존성 추가 |
| `settings.gradle.kts` | market-collector, realtime-gateway 모듈 등록 |
| `init/timescaledb/02-market-schema.sql` | ticks 하이퍼테이블 + candles_1m 연속집계 뷰 |
| `services/market-collector/` | KIS WS 클라이언트·Tick 엔티티·Kafka 발행·Redis 캐시·REST API |
| `services/realtime-gateway/` | STOMP WebSocket + Kafka 소비 → 브라우저 푸시 |
| `client/web/` | React + Vite + TradingView Lightweight Charts + STOMP |

## 수정 이력
- `KisWebSocketClient.kt`: `isActive` → `currentCoroutineContext().isActive` 수정
- `KisWebSocketClient.kt`: `awaitSingleOrNull` import 경로 수정

## 런타임 검증 방법 (Docker 기동 후)

```bash
# 1. 인프라 기동
cp .env.example .env  # KIS_APP_KEY, KIS_APP_SECRET 입력 확인
docker compose up -d

# 2. 서비스 기동
./gradlew :services:market-collector:bootRun
./gradlew :services:realtime-gateway:bootRun

# 3. REST 검증
curl http://localhost:8082/market/quote/005930

# 4. WebSocket 검증
# 브라우저에서 client/web 실행: cd client/web && npm install && npm run dev
```
