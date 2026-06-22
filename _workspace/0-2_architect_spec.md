# 스펙: 0-2 docker-compose.yml (Kafka·Redis·PostgreSQL·TimescaleDB·MongoDB)

> 작성: architect | 대상 구현자: backend-dev | 검증: qa
> 범위: **WBS 0-2 한 개만.** 로컬 개발용 인프라 컨테이너 5종을 `docker-compose.yml` 한 파일로 띄운다. 애플리케이션 서비스(api-gateway 등 13개)는 **이 단계에서 컨테이너로 올리지 않는다**(0-5 이후 담당). 관측 스택(Prometheus·Grafana·Loki·Tempo)도 이번 범위가 **아니다**(Phase 6).

---

## 0. 한 줄 요약

StockPulse 백엔드가 로컬에서 기동하려면 **Kafka·Redis·PostgreSQL·TimescaleDB·MongoDB** 5개 저장소가 떠 있어야 한다. 이 5종을 `docker compose up -d` 한 번으로 띄우고, 각 컨테이너가 `healthy`가 되도록 docker-compose.yml(+ `.env.example` + `init/` 초기화 스크립트)을 만든다. 컨테이너는 실행만 명세하고, 애플리케이션 코드/스키마(테이블 DDL)는 **만들지 않는다**(각 서비스가 Flyway/JPA로 추후 생성).

> 용어 한 줄 설명
> - **healthcheck**: 컨테이너가 "그냥 켜짐"이 아니라 "실제로 요청을 받을 준비가 됨(healthy)"인지 주기적으로 검사하는 Docker 기능. 다른 컨테이너가 `depends_on: condition: service_healthy`로 기다릴 수 있다.
> - **named volume(명명 볼륨)**: Docker가 관리하는 이름 붙은 데이터 저장 공간. 컨테이너를 지우고 다시 만들어도 데이터(DB 내용)가 살아남는다. (호스트 경로 bind mount와 달리 OS·경로에 안 묶여 Windows에서도 안전.)
> - **KRaft**: Kafka가 메타데이터를 스스로 관리하는 모드. 예전엔 Zookeeper라는 별도 컨테이너가 필수였지만, KRaft는 **Zookeeper 없이** Kafka 단독으로 동작한다.

---

## 1. 대상

- **만들 파일 (3개)**
  1. `c:\study\StockPulse\docker-compose.yml` ★ 핵심 산출물 (1개)
  2. `c:\study\StockPulse\.env.example` ★ 비밀번호·포트 등 환경변수 템플릿 (커밋 O, 실제 `.env`는 `.gitignore`로 제외됨 — `.gitignore` 2~4행에서 `.env`/`.env.*` 제외, `!.env.example`만 추적 확인됨)
  3. `c:\study\StockPulse\init/` 초기화 스크립트 디렉토리 (아래 1-1 참조)
- **만들지 않을 것**: 애플리케이션 서비스 컨테이너, 테이블 DDL/마이그레이션(Flyway), 관측 스택, Dockerfile.
- 의존 작업: **0-1 완료**(Gradle 골격). 단 docker-compose는 Gradle과 독립이라 기능적 선후 의존은 약함. 후행 0-4(API 키 호출 검증)·Phase 1~ 가 이 인프라 위에서 동작.
- 근거: `docs/02_시스템아키텍처.md` §5(Kafka 토픽), §3(서비스-저장소 매핑), `docs/04_DB설계.md` §1(저장소 역할)·§5(Redis 키), `docs/09_개발환경.md`(`docker compose up -d`로 5종 기동 명시), `todo.md` 41행.

### 1-1. init/ 초기화 스크립트 — 필요 여부 결정

| 저장소 | init 스크립트 필요? | 무엇을 / 왜 |
|--------|--------------------|------------|
| **TimescaleDB** | **필요 (권장)** | TimescaleDB 이미지는 PostgreSQL 위에 확장(extension)을 얹은 것. 컨테이너 최초 기동 시 `/docker-entrypoint-initdb.d/`에 둔 `.sql`이 자동 실행된다. 여기서 **`CREATE EXTENSION IF NOT EXISTS timescaledb;`** 만 보장한다. (timescaledb 이미지는 보통 자동 활성화하지만, 명시적으로 확장 보장 SQL을 두면 qa가 "확장 켜짐"을 검증하기 쉽다.) ⚠️ **하이퍼테이블(`ticks`)·연속집계(`candles`) DDL은 여기 넣지 않는다** — 그건 Market Collector 서비스(Phase 1)의 마이그레이션 책임. 이 단계는 "확장만 켜진 빈 DB" 까지.
| PostgreSQL(트랜잭션용) | 선택 (DB 분리 시 필요) | 아래 2-3 "DB 분리 전략" 결정에 따라 `CREATE DATABASE` 스크립트가 필요할 수 있음. |
| MongoDB | 불필요 | 앱이 컬렉션을 알아서 생성. 인증 사용자만 환경변수로 생성됨. |
| Redis / Kafka | 불필요 | 스키마 없음. |

- 제안 디렉토리 구조:
  ```
  init/
  ├── timescaledb/
  │   └── 01-extension.sql      # CREATE EXTENSION IF NOT EXISTS timescaledb;
  └── postgres/
      └── 01-create-databases.sql  # (2-3에서 "DB 분리" 택할 경우에만)
  ```
- compose에서 각 DB 컨테이너의 `/docker-entrypoint-initdb.d`에 해당 디렉토리를 **read-only bind mount**(`:ro`)로 연결한다.

---

## 2. 컨테이너 공통 결정 사항 (먼저 읽을 것)

### 2-1. 이미지 태그 정책
- **반드시 고정 태그(pinned tag)를 쓴다. `latest` 금지.** (`latest`는 언젠가 깨진다. 재현성·qa 판정 안정성 위해 버전 고정.)
- 아래 표의 태그는 **권장 안정 버전**이다. backend-dev는 이 태그로 시작하되, `docker pull`이 실패하면(태그 미존재) 같은 메이저의 최신 패치로 올리고 그 사실을 보고하라.

### 2-2. 네트워크
- **사용자 정의 bridge 네트워크 1개**를 명시 생성한다. 이름 제안: `stockpulse-net`.
- 이유: 같은 네트워크에 속한 컨테이너끼리 **서비스명으로 DNS 통신**(예: 앱이 `kafka:9092`, `postgres:5432`로 접속) 가능. 추후 0-5에서 앱 컨테이너를 추가해도 같은 네트워크에 붙이면 됨.

### 2-3. ⚠️ PostgreSQL "DB per service" vs 단일 인스턴스 — 결정 필요 (리더 확인 권장)
- `docs/04_DB설계.md` §1은 **"각 서비스가 자신의 데이터를 소유(DB per service)"** 라고 명시. PostgreSQL을 쓰는 서비스는 6개(Auth/User, Account, Portfolio, Trading, Community, Notification).
- 로컬 개발에서 이를 구현하는 두 방법:
  - **(A) 단일 PostgreSQL 컨테이너 + 서비스별 database 분리** ← **본 스펙 권장(로컬 한정)**. 컨테이너 1개 안에 `auth`, `account`, `portfolio`, `trading`, `community`, `notification` 6개 database를 만들어 논리적으로 분리. 로컬 리소스 절약 + "DB per service" 정신 유지. init 스크립트로 `CREATE DATABASE` 6개 실행.
  - (B) 서비스마다 PostgreSQL 컨테이너 1개씩(6개) — 운영(K8s)과 가장 유사하나 로컬에선 과함(메모리·포트 낭비).
- **본 스펙은 (A)로 확정 제안.** TimescaleDB는 Market Collector 전용이므로 **별도 컨테이너**(틱 대량 데이터 + 확장 필요)로 유지. → 즉 "트랜잭션용 PostgreSQL 1개 + 시계열용 TimescaleDB 1개"의 2개 PG 계열 컨테이너.
- 만약 (B)나 "로컬도 운영과 동일하게"를 원하면 설계 변경이므로 리더 확인. **본 스펙은 (A) 기준으로 이하 명세.**

### 2-4. 포트 충돌 회피 원칙
- 각 저장소의 **기본 포트를 호스트에 그대로 노출**하되, 충돌 가능성을 주석으로 남긴다.
- 특히 **PostgreSQL(5432)과 TimescaleDB(5432)가 같은 기본 포트**이므로 둘을 같은 호스트 포트로 노출하면 **충돌한다.** → TimescaleDB는 **호스트 포트를 5433으로** 매핑(컨테이너 내부는 5432 유지). 이 점 주석 필수.
- 모든 포트 매핑은 `.env`의 변수로 빼서(예: `${POSTGRES_PORT:-5432}`) 사용자가 충돌 시 쉽게 바꾸게 한다.

---

## 3. 컨테이너별 상세 명세 (backend-dev가 그대로 작성 가능하게)

> 아래는 각 service 블록에 **무엇을 담아야 하는가**의 요구사항이다. 실제 YAML은 backend-dev가 작성.
> compose 파일 상단의 `version:` 키는 **넣지 않는다**(Compose v2/v5에서 obsolete 경고 발생). 최상위는 `services:`, `volumes:`, `networks:` 만.

### 3-1. Kafka (KRaft 모드 — **Zookeeper 미사용으로 확정**)

- **결정 근거**: `docs/02_시스템아키텍처.md` §2 구성도에 `Kafka (KRaft)`로 명시됨. → Zookeeper 컨테이너를 만들지 않는다. 컨테이너 1개로 단순화(로컬 리소스 절약, 구성도와 일치).
- **이미지(권장)**: `confluentinc/cp-kafka:7.7.1` (Confluent 7.7.x는 KRaft 단일 노드 구성을 환경변수로 지원). 또는 `apache/kafka:3.8.0`(아파치 공식, KRaft 기본). → **backend-dev는 둘 중 하나 선택**. 익숙도·예제 풍부함 기준으로 `confluentinc/cp-kafka:7.7.1` 우선 권장.
- **서비스명**: `kafka`
- **포트 매핑**:
  - `9092:9092` — 호스트(앱이 로컬에서 직접 접속)용 리스너.
  - 내부 컨테이너 간 통신용 리스너(예: 9093 컨트롤러, 29092 내부 broker)는 호스트로 노출하지 않는다.
- **리스너 구성(중요)**: KRaft + 멀티 리스너로 "호스트에서 접속"과 "컨테이너 내부 접속"을 둘 다 가능하게 해야 한다. 환경변수로 다음을 설정:
  - 노드 역할: broker + controller 겸용(`KAFKA_PROCESS_ROLES=broker,controller`)
  - `KAFKA_NODE_ID=1`, `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093`
  - 리스너: `PLAINTEXT`(내부 `kafka:29092`), `CONTROLLER`(`9093`), `PLAINTEXT_HOST`(외부 `localhost:9092`)
  - `KAFKA_ADVERTISED_LISTENERS`: 내부 소비자는 `PLAINTEXT://kafka:29092`, 호스트 소비자는 `PLAINTEXT_HOST://localhost:9092`
  - `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`, `KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER`, `KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT`
  - 단일 노드이므로 복제 계수 1 강제: `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1`, `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1`, `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1`
  - KRaft 클러스터 ID: `CLUSTER_ID`(고정 문자열, `.env`로 관리) 또는 이미지가 자동 포맷하도록 설정.
- **볼륨**: named volume `kafka-data` → 컨테이너의 로그 디렉토리(`/var/lib/kafka/data`)에 마운트.
- **healthcheck**: `kafka-broker-api-versions --bootstrap-server localhost:9092` 가 성공하면 healthy. (또는 `kafka-topics --bootstrap-server localhost:9092 --list`.) `start_period`를 넉넉히(예: 20~30s) 줘서 기동 중 false-negative 방지.
- **depends_on**: 없음(최상위 인프라).
- > 참고: 토픽은 `docs/02` §5에 8개(`market.tick`·`news.raw`·`fundamentals.raw`·`insight.updated`·`order.events`·`account.events`·`portfolio.updated`·`community.events`). **이 단계에서는 토픽을 미리 만들지 않는다**(각 서비스가 기동 시 자동 생성하거나 Phase 1+에서 토픽 생성 작업으로 처리). 자동 토픽 생성을 허용하려면 `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`로 두되, 파티션 키 설계(종목코드/사용자ID)는 토픽을 명시 생성하는 후속 작업에서 다룸을 주석으로 남겨라.

### 3-2. Redis

- **역할 근거**: `docs/04` §1·§5 — 시세 캐시(`quote:{symbol}`), 랭킹(`ranking:{period}` Sorted Set), Rate Limit(`ratelimit:{ip}`), Refresh 토큰(`refresh:{userId}`), Pub/Sub(`pubsub:market/chat/insight:{symbol}`).
- **이미지(권장)**: `redis:7.4-alpine`
- **서비스명**: `redis`
- **포트**: `6379:6379` (기본 포트, 충돌 가능 주석)
- **환경변수/실행 옵션**:
  - 로컬 개발이므로 비밀번호는 선택. 단 일관성 위해 `--requirepass ${REDIS_PASSWORD}` 권장(.env로 관리). 비밀번호를 쓰면 healthcheck·앱 접속에 반영해야 함.
  - 영속화: Pub/Sub은 휘발이라도 캐시·랭킹은 재시작 시 살아있는 게 편리 → `--appendonly yes`(AOF) 권장.
- **볼륨**: named volume `redis-data` → `/data`.
- **healthcheck**: `redis-cli ping`(비밀번호 사용 시 `redis-cli -a $REDIS_PASSWORD ping`)이 `PONG` 반환하면 healthy.
- **depends_on**: 없음.

### 3-3. PostgreSQL (트랜잭션용, 6개 service DB 분리 — 2-3의 (A))

- **역할 근거**: `docs/04` §1·§2 — Auth/User·Account·Portfolio·Trading·Community·Notification의 트랜잭션 테이블 + 각 서비스 Outbox.
- **이미지(권장)**: `postgres:16.4` (16.x LTS 계열, alpine 변형도 가능)
- **서비스명**: `postgres`
- **포트**: `${POSTGRES_PORT:-5432}:5432`
- **환경변수**:
  - `POSTGRES_USER=${POSTGRES_USER}` (예: `stockpulse`)
  - `POSTGRES_PASSWORD=${POSTGRES_PASSWORD}`
  - `POSTGRES_DB=${POSTGRES_DB}` (기본 DB, 예: `stockpulse`. 부팅용 1개. 서비스별 DB는 init 스크립트로 추가 생성.)
- **init 스크립트**: `init/postgres/01-create-databases.sql`를 `/docker-entrypoint-initdb.d/`에 `:ro` 마운트. 내용 요구: 서비스별 database 6개 생성 — `auth`, `account`, `portfolio`, `trading`, `community`, `notification`. (소유자는 `POSTGRES_USER`.) ⚠️ **테이블 DDL은 넣지 않는다**(각 서비스 마이그레이션 책임).
- **볼륨**: named volume `postgres-data` → `/var/lib/postgresql/data`.
- **healthcheck**: `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}` 성공 시 healthy.
- **depends_on**: 없음.

### 3-4. TimescaleDB (시계열 — 별도 컨테이너)

- **역할 근거**: `docs/04` §1·§3 — Market Collector 소유. `ticks` 하이퍼테이블, `candles` 연속집계.
- **이미지(권장)**: `timescale/timescaledb:2.17.2-pg16` (TimescaleDB 2.17.x + PostgreSQL 16 베이스 — 위 일반 postgres와 메이저 16으로 정렬).
- **서비스명**: `timescaledb`
- **포트**: `${TIMESCALE_PORT:-5433}:5432` ⚠️ **호스트 포트 5433** (postgres 5432와 충돌 회피 — 2-4 참조. 주석 필수: "내부는 5432, 호스트만 5433").
- **환경변수**: `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB=${TIMESCALE_DB:-market}` (예: `market`). postgres 컨테이너와 **다른 변수 세트**를 쓰도록 `.env`에서 `TIMESCALE_*` 접두로 분리 권장.
- **init 스크립트**: `init/timescaledb/01-extension.sql`를 `/docker-entrypoint-initdb.d/`에 `:ro` 마운트. 내용: `CREATE EXTENSION IF NOT EXISTS timescaledb;` 만. (하이퍼테이블 DDL 제외 — Phase 1 책임.)
- **볼륨**: named volume `timescaledb-data` → `/var/lib/postgresql/data`. (postgres 볼륨과 **반드시 분리된 별도 볼륨**.)
- **healthcheck**: `pg_isready -U ${TIMESCALE_USER} -d ${TIMESCALE_DB}` 성공 시 healthy.
- **depends_on**: 없음.

### 3-5. MongoDB

- **역할 근거**: `docs/04` §1·§4 — News Collector·Insight 소유. `news`·`insights`·`backtests` 컬렉션(비정형 문서).
- **이미지(권장)**: `mongo:7.0`
- **서비스명**: `mongo`
- **포트**: `${MONGO_PORT:-27017}:27017`
- **환경변수**:
  - `MONGO_INITDB_ROOT_USERNAME=${MONGO_USER}`
  - `MONGO_INITDB_ROOT_PASSWORD=${MONGO_PASSWORD}`
  - `MONGO_INITDB_DATABASE=${MONGO_DB:-stockpulse}` (앱이 컬렉션 자동 생성하므로 DB만 지정)
- **볼륨**: named volume `mongo-data` → `/data/db`.
- **healthcheck**: `mongosh --eval "db.adminCommand('ping')"` (인증 사용 시 `-u/-p` 또는 `--quiet`와 함께) 성공 시 healthy. (mongo:7 이미지는 `mongosh` 포함.)
- **depends_on**: 없음.

---

## 4. 최상위 volumes / networks 명세

- **`networks:`** — `stockpulse-net` (driver: bridge) 1개 정의. 모든 서비스가 이 네트워크에 소속.
- **`volumes:`** — named volume 5개 선언: `kafka-data`, `redis-data`, `postgres-data`, `timescaledb-data`, `mongo-data`. (데이터 영속화: 컨테이너 재생성에도 DB 내용 유지.)

---

## 5. `.env.example` 명세 (담아야 할 변수)

> 실제 `.env`는 `.gitignore`로 제외됨. `.env.example`만 커밋(값은 더미/기본값). 사용자가 복사해 `.env`로 쓴다.

- **포트**: `POSTGRES_PORT=5432`, `TIMESCALE_PORT=5433`, `MONGO_PORT=27017`, `REDIS_PORT=6379`, `KAFKA_PORT=9092`
- **PostgreSQL(트랜잭션)**: `POSTGRES_USER=stockpulse`, `POSTGRES_PASSWORD=changeme`, `POSTGRES_DB=stockpulse`
- **TimescaleDB**: `TIMESCALE_USER=stockpulse`, `TIMESCALE_PASSWORD=changeme`, `TIMESCALE_DB=market`
- **MongoDB**: `MONGO_USER=stockpulse`, `MONGO_PASSWORD=changeme`, `MONGO_DB=stockpulse`
- **Redis**: `REDIS_PASSWORD=changeme` (비밀번호 미사용 결정 시 생략)
- **Kafka**: `CLUSTER_ID=` (KRaft 클러스터 ID. `kafka-storage random-uuid`로 생성한 값을 넣거나, 이미지 자동생성 사용 시 주석으로 설명)
- > 주의: `.env.example`의 비밀번호는 전부 `changeme` 더미. 실제 키(KIS/네이버/DART, JWT_SECRET)는 0-3에서 별도 추가 — **이 단계 범위 아님**.

---

## 6. 완료 기준 (DoD) — qa가 PASS/FAIL 판정 가능

> 환경: 이 PC에 Docker 29.2.1 + Compose v5.0.2 설치됨. `docker compose`(v2 문법) 사용.

- [ ] **D1** `c:\study\StockPulse\docker-compose.yml`, `.env.example`, `init/timescaledb/01-extension.sql`이 존재한다. (2-3 (A) 채택 시 `init/postgres/01-create-databases.sql`도 존재.)
- [ ] **D2** compose 파일 최상위에 `services`(5개: kafka, redis, postgres, timescaledb, mongo), `volumes`(5개), `networks`(stockpulse-net)가 있다. `version:` 키는 없다.
- [ ] **D3** 5개 서비스 모두 **고정 태그**(`latest` 아님) 이미지를 사용한다.
- [ ] **D4** `cp .env.example .env` 후 **`docker compose config` 가 에러·경고 없이 통과**한다(변수 치환 정상, obsolete version 경고 없음). ← **최소 필수 검증.**
- [ ] **D5** PostgreSQL(host 5432)과 TimescaleDB(host **5433**)의 호스트 포트가 서로 다르다(충돌 없음). 모든 포트가 `.env` 변수로 치환된다.
- [ ] **D6** 5개 서비스 모두 `healthcheck`가 정의되어 있고, named volume과 `stockpulse-net` 네트워크에 연결되어 있다.
- [ ] **D7** Kafka 서비스에 **Zookeeper 컨테이너가 없다**(KRaft 단일 노드). `KAFKA_PROCESS_ROLES`에 `controller`가 포함된다.
- [ ] **D8 (실행 검증 — qa 판단)** `docker compose up -d` 후 일정 시간(예: 90s) 내 **5개 컨테이너 모두 `healthy`**가 된다 (`docker compose ps`의 STATUS가 `healthy`). qa가 실제 up까지 수행할지 판단하되, **최소 D4(config)는 필수**, D8은 가능하면 수행.
- [ ] **D9 (실행 검증, D8 수행 시)** TimescaleDB 컨테이너에서 `SELECT extname FROM pg_extension WHERE extname='timescaledb';`가 1행을 반환한다(확장 활성). PostgreSQL에서 `\l`로 6개 서비스 DB가 생성됨을 확인(2-3 (A) 채택 시).
- [ ] **D10 (실행 검증, D8 수행 시)** `docker compose down` 후 다시 `up -d` 했을 때 데이터가 유지된다(named volume 영속화). — 선택 검증.

> qa 검증 절차 권장: D1~D3·D5~D7은 파일 내용 확인, D4는 `docker compose config` 실행, D8~D10은 실제 컨테이너 기동 후 판정.

---

## 7. 참고 문서 + 문서 누락/주의사항

### 근거 문서 경로
- `c:\study\StockPulse\todo.md` 41행 (0-2 항목: Kafka·Redis·PostgreSQL·TimescaleDB·MongoDB)
- `c:\study\StockPulse\docs\02_시스템아키텍처.md` §2 구성도(`Kafka (KRaft)` 명시), §3 서비스-저장소 매핑, §5 Kafka 토픽 8개
- `c:\study\StockPulse\docs\04_DB설계.md` §1 저장소별 역할, §2 PostgreSQL 서비스별 테이블·Outbox, §3 TimescaleDB(하이퍼테이블/연속집계), §4 MongoDB 컬렉션, §5 Redis 키 설계
- `c:\study\StockPulse\docs\09_개발환경.md` (`docker compose up -d`로 5종 기동 명시, 사전요구 Docker Desktop)
- `c:\study\StockPulse\.gitignore` 2~4행(`.env` 제외 / `.env.example` 추적 확인)

### ⚠️ 문서 누락 / 결정 필요 (리더 확인 권장)
1. **각 저장소 정확한 버전이 docs에 없음.** 본 스펙에서 권장 태그 제안 확정: Kafka `confluentinc/cp-kafka:7.7.1`(또는 `apache/kafka:3.8.0`), Redis `7.4-alpine`, PostgreSQL `16.4`, TimescaleDB `2.17.2-pg16`, MongoDB `7.0`. 구현 후 `09_개발환경.md`에 확정 버전 반영 필요(architect 동기화).
2. **PostgreSQL DB per service 구현 방식(2-3).** 로컬은 (A) 단일 컨테이너 + 6 DB 분리로 확정 제안. 운영(K8s)과 동일하게 컨테이너 분리를 원하면 설계 변경 — 리더 확인.
3. **Redis 비밀번호 사용 여부.** 로컬 개발 편의상 생략 가능하나 일관성 위해 `requirepass` 권장. 결정 시 healthcheck·앱 접속 설정에 일관 반영 필요.
4. **Kafka 이미지 선택(Confluent vs Apache 공식).** 둘 다 KRaft 지원. backend-dev가 익숙도 기준 택1, 선택 결과를 보고.
5. **토픽 사전 생성 여부.** 본 단계는 토픽 미생성(auto-create 허용 또는 후속 작업). 파티션 키 설계(종목코드/사용자ID, `docs/02` §5)를 반영한 명시적 토픽 생성은 별도 후속 작업으로 분리 — 이 점 누락 아님, 의도적 범위 제외.
6. **`09_개발환경.md`는 "JDK 17+"만 있고 인프라 포트·접속주소 미작성.** 구현 후 접속 주소표(예: `localhost:5432` 등)를 09에 채울 것(동기화 작업).
