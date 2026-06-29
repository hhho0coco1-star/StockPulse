# Phase 2 — 인사이트 엔진 아키텍트 스펙

## 서비스 구성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| news-collector | :8083 | 네이버 뉴스 API → 감성 분석 → MongoDB + Kafka |
| fundamentals-collector | :8084 | DART API → 재무 데이터 → MongoDB + Kafka |
| insight-service | :8085 | 4축 점수 산출 → MongoDB 저장 → REST API |

## Kafka 토픽

| 토픽 | 발행자 | 소비자 |
|------|--------|--------|
| `news.collected` | news-collector | insight-service |
| `fundamentals.updated` | fundamentals-collector | insight-service |
| `market.tick` (기존) | market-collector | insight-service (모멘텀 계산용) |

## MongoDB 컬렉션

### news
```json
{ "symbol": "005930", "title": "...", "url": "...", "source": "naver",
  "publishedAt": "ISO8601", "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
  "keywords": [], "score": 1 }
```

### fundamentals
```json
{ "symbol": "005930", "year": 2025, "quarter": 4,
  "revenue": 300000000, "operatingProfit": 20000000,
  "revenueGrowthYoY": 8.1, "opProfitGrowthYoY": 12.0,
  "updatedAt": "ISO8601" }
```

### insights
```json
{ "_id": "005930", "symbol": "005930", "totalScore": 78, "grade": "POSITIVE",
  "momentum":    { "score": 80, "return5d": 4.2, "volumeRatio": 1.8 },
  "fundamental": { "score": 72, "opGrowthYoY": 12.0, "salesGrowthYoY": 8.1 },
  "valuation":   { "score": 60, "verdict": "NEUTRAL" },
  "news":        { "score": 75, "positive": 3, "negative": 0 },
  "updatedAt": "ISO8601" }
```

## 4축 점수 산출 로직

### 모멘텀 (0~100)
- 5일 수익률 > +5%: +40, +2%: +25, -2%: 10, 이하: 0
- 거래량 비율 > 2.0: +30, > 1.5: +20, 이하: +10
- 기준: 50점

### 실적 (0~100)
- 영업이익 성장률 > 20%: 90, > 10%: 75, > 0%: 60, 음수: 30
- 매출 성장률 > 10%: +10 보너스

### 밸류에이션 (0~100)
- DART 데이터 없으면 기본 60점
- 영업이익률 > 15%: 80, > 10%: 70, > 5%: 60, 이하: 40

### 뉴스 (0~100)
- (긍정수 / 전체수) × 100, 기사 없으면 50

### 종합 점수
- (모멘텀 × 0.3 + 실적 × 0.3 + 밸류에이션 × 0.2 + 뉴스 × 0.2)
- 80 이상: STRONG_BUY, 65 이상: POSITIVE, 45 이상: NEUTRAL, 미만: NEGATIVE

## REST API 계약

| 메서드 | 경로 | 응답 |
|--------|------|------|
| GET | `/insights/{symbol}` | 종합 전망 카드 |
| GET | `/insights/{symbol}/factors` | 4축 상세 |
| GET | `/insights/strong?market=KR&page=0` | 강세 종목 리스트 (score ≥ 65) |

## DoD

| # | 항목 |
|---|------|
| D1 | `./gradlew build` 성공 |
| D2 | news-collector :8083 기동, 네이버 뉴스 수집 1회 실행 |
| D3 | fundamentals-collector :8084 기동, DART 재무 수집 1회 실행 |
| D4 | insight-service :8085 기동 |
| D5 | `GET /insights/{symbol}` 응답 (MongoDB 인사이트 카드) |
| D6 | `GET /insights/strong` 종목 리스트 응답 |
