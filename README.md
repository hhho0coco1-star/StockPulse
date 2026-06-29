# StockPulse

> "보고(인사이트) → 토론하고(커뮤니티) → 행동하는(모의투자)" 하나의 루프로 연결된 실시간 주식 플랫폼

실시간 주식 시세·인사이트·모의투자·커뮤니티를 하나로 묶은 **Kotlin + Spring Boot MSA** 포트폴리오 프로젝트.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **언어/프레임워크** | Kotlin 1.9.25 · Spring Boot 3.3.5 · Spring Cloud Gateway |
| **비동기** | Kotlin Coroutines · WebFlux |
| **메시지 브로커** | Apache Kafka (KRaft) · Kafka Streams |
| **데이터베이스** | PostgreSQL 16 · TimescaleDB 2.17 · MongoDB 7 |
| **캐시/실시간** | Redis 7 · WebSocket · SSE |
| **외부 API** | KIS Open API (실시간 시세) · 네이버 검색 API (뉴스) · DART API (재무) |
| **클라이언트** | React (Vite · Tailwind · TradingView) · React Native (Expo) |
| **푸시** | FCM (Firebase Cloud Messaging) |
| **인프라** | Docker Compose · Kubernetes + HPA · Helm |
| **CI/CD** | GitHub Actions |
| **관측** | Prometheus · Grafana · Loki · Tempo |

---

## 서비스 구성 (13개)

```
클라이언트 (React 웹 / React Native 앱)
        ↓
  API Gateway :8080  ← JWT 검증 · 라우팅 · Rate Limit
        ↓
┌─────────────────────────────────────────────┐
│ Auth/User    :8081  회원가입·로그인·워치리스트  │
│ Market       :8082  종목검색·현재가·캔들       │
│ News Col.    :8083  뉴스 수집 → Kafka         │
│ Fundamentals :8084  DART 재무 수집 → Kafka    │
│ Insight      :8085  인사이트 카드·점수         │
│ Trading      :8086  모의주문·Saga 오케스트레이터│
│ Account      :8087  가상잔고·원장              │
│ Portfolio    :8088  보유종목·평가손익           │
│ Ranking      :8089  수익률 랭킹                │
│ Realtime GW  :8090  WebSocket 시세·채팅 푸시   │
│ Community    :8091  게시글·댓글·좋아요          │
│ Notification :8092  알림규칙·FCM               │
└─────────────────────────────────────────────┘
        ↓
 Kafka (KRaft) · Redis · PostgreSQL · TimescaleDB · MongoDB
```

---

## 로컬 실행 방법

### 사전 요구사항
- JDK 21+ (또는 Gradle Toolchain 자동 프로비저닝)
- Docker Desktop

### 1. 인프라 기동

```bash
cp .env.example .env   # .env 파일 생성 후 API 키 입력
docker compose up -d   # Kafka · Redis · PostgreSQL · TimescaleDB · MongoDB
```

### 2. 빌드

```bash
./gradlew build
```

### 3. 서비스 기동

```bash
# 각 서비스 개별 기동
./gradlew :services:api-gateway:bootRun    # 포트 8080
./gradlew :services:auth-service:bootRun   # 포트 8081
```

---

## 인프라 포트 정보

| 서비스 | 호스트 포트 |
|--------|------------|
| API Gateway | 8080 |
| Auth Service | 8081 |
| Kafka | 9092 |
| Redis | 6379 |
| PostgreSQL | 5432 |
| TimescaleDB | 5433 |
| MongoDB | 27017 |

---

## 개발 진행 현황

| Phase | 내용 | 상태 |
|-------|------|------|
| 0 | 기반 구축 (인프라·Gateway·Auth 골격) | 🔄 진행중 |
| 1 | 시세 파이프라인 (KIS WS → Kafka → Redis → WebSocket) | 예정 |
| 2 | 인사이트 엔진 (뉴스·재무 수집 + Kafka Streams) | 예정 |
| 3 | 모의투자 Saga (주문·잔고·보유·랭킹) | 예정 |
| 4 | 커뮤니티·알림 (채팅·워치리스트·FCM) | 예정 |
| 5~9 | 안정성·관측·부하테스트·배포·앱 | 예정 |

---

## 관련 문서

| 문서 | 내용 |
|------|------|
| [01_기획서](docs/01_기획서.md) | 프로젝트 목적·배경·핵심 기능 |
| [02_시스템아키텍처](docs/02_시스템아키텍처.md) | 서비스 구성도·데이터 흐름 |
| [03_API설계](docs/03_API설계.md) | REST·WebSocket 엔드포인트 계약 |
| [04_DB설계](docs/04_DB설계.md) | 저장소별 테이블·스키마 |
| [05_WBS_개발계획](docs/05_WBS_개발계획.md) | Phase별 작업 분해·완료 기준 |
| [06_와이어프레임](docs/06_와이어프레임.md) | 화면 설계·UI 흐름 |
| [07_기술선택이유](docs/07_기술선택이유.md) | 기술 선택 근거 |
| [08_시퀀스다이어그램](docs/08_시퀀스다이어그램.md) | 주요 흐름 시퀀스 |
