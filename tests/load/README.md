# StockPulse k6 부하 테스트

## 전제 조건

- k6 설치: https://grafana.com/docs/k6/latest/get-started/installation/
- 모든 서비스 기동 (`docker compose up -d` + 14개 Spring Boot 앱)
- JWT 토큰 발급

## 환경 변수 설정

```bash
# 로그인으로 access token 발급
export K6_JWT_TOKEN="$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"Test1234!"}' | jq -r '.data.accessToken')"

export K6_BASE_URL="http://localhost:8080"
```

## 시나리오 실행

```bash
# 시나리오 A: 시세 조회 (read-heavy, 최대 1000 RPS)
k6 run tests/load/scenarioA_quote.js

# 시나리오 B: 모의투자 주문 (Saga, 202 응답)
# 주의: 실행 전 잔고 충전 필요
curl -X POST http://localhost:8080/api/v1/account/reset \
  -H "Authorization: Bearer $K6_JWT_TOKEN"
k6 run tests/load/scenarioB_order.js

# 시나리오 C: 토론방 게시글 작성
k6 run tests/load/scenarioC_post.js

# 혼합 부하 (A+B+C 동시)
k6 run tests/load/all.js

# 결과 JSON 저장
k6 run --summary-export=tests/load/out/A_$(date +%Y%m%d).json tests/load/scenarioA_quote.js
```

## 결과 해석

| 지표 | 위치 | 기준 |
|------|------|------|
| p95 응답시간 | k6 summary `http_req_duration` | A < 300ms, B/C < 500ms |
| 에러율 | `http_req_failed` | < 1% |
| 비즈니스 거절 | `business_rejected` counter | 별도 집계 (잔고부족 409) |

## Grafana 연동

테스트 실행 중 http://localhost:3000 → StockPulse Overview 대시보드에서 실시간 확인:
- JVM Heap / RPS / p95 latency / 5xx rate 패널

## 로컬 환경 스모크 테스트 (k6 검증)

```bash
# 서비스 없이 스크립트 문법 검증
k6 inspect tests/load/scenarioA_quote.js

# VU 1, 1 iteration으로 빠른 연기 테스트
K6_JWT_TOKEN="dummy" k6 run --vus 1 --iterations 1 tests/load/scenarioA_quote.js
```
