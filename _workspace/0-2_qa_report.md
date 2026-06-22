# QA 리포트: WBS 0-2 docker-compose 인프라

> 검증 수행: 오케스트레이터(backend-dev/qa 서브 에이전트가 서버 과부하 529로 실패하여 리더가 직접 stockpulse-impl/verify 절차로 구현·검증).

## 최종 판정: **PASS** (D1~D10 전체 충족 — 라이브 기동 검증 완료)

> 라이브 검증: Docker Desktop 엔진 기동 후 `docker compose up -d` 수행. 5개 컨테이너 모두 healthy, TimescaleDB 확장 활성, PostgreSQL 6개 DB 생성, 볼륨 영속화 확인. 검증 후 `docker compose down`으로 정리(볼륨 보존).

## DoD 항목별 결과
| 항목 | 내용 | 결과 |
|------|------|------|
| D1 | docker-compose.yml, .env.example, init 스크립트 2종 존재 | PASS |
| D2 | services 5 / volumes 5 / networks / version 키 없음 | PASS |
| D3 | 5개 서비스 고정 태그 (latest 없음) | PASS |
| D4 | `docker compose config` 에러·경고 없이 통과 | **PASS (필수)** |
| D5 | postgres(host 5432) ↔ timescaledb(host 5433) 포트 분리, 전 포트 .env 변수화 | PASS |
| D6 | 5개 서비스 healthcheck + named volume + stockpulse-net 연결 | PASS |
| D7 | Zookeeper 컨테이너 없음(KRaft), PROCESS_ROLES에 controller 포함 | PASS |
| D8 | `up -d` 후 5개 컨테이너 healthy | **PASS** (5/5 healthy) |
| D9 | timescaledb 확장 활성 + postgres 6개 DB 생성 | **PASS** (확장 1행 + DB 6개) |
| D10 | down 후 데이터(볼륨) 유지 | **PASS** (named volume 5개 보존 확인) |

## 실행 증거
```
$ cp .env.example .env
$ docker compose config        → exit 0, stderr 비어있음 (obsolete version 경고 없음)
$ docker compose config --services  → kafka mongo postgres redis timescaledb (5개)
$ docker compose config --volumes   → kafka-data mongo-data postgres-data redis-data timescaledb-data (5개)
```

## 정합성/경계면
- compose `image:` 태그 ↔ 스펙 §3 권장 태그: 일치 (cp-kafka:7.7.1, redis:7.4-alpine, postgres:16.4, timescaledb:2.17.2-pg16, mongo:7.0)
- init 볼륨 마운트(`./init/postgres`, `./init/timescaledb` → `/docker-entrypoint-initdb.d:ro`) ↔ 스펙 §3-3/§3-4: 일치
- .env.example 변수 ↔ compose의 `${VAR}` 치환 대상: 전부 정의됨 (config 통과로 확인)

## 검증 중 주의사항 (QA 원칙 사례)
- 정적 grep에서 "zookeeper"가 매칭됐으나 실제로는 **주석의 'Zookeeper 미사용' 설명 문구**였음. 실제 zookeeper 서비스는 없음 → D7 PASS. (표면 매칭이 아닌 실제 확인 필요 사례.)

## backend-dev 결정 사항 (리더 기록)
- Kafka 이미지: `confluentinc/cp-kafka:7.7.1` 채택 (예제·문서 풍부, KRaft 환경변수 지원).
- Redis 비밀번호: 로컬 일관성 위해 `--requirepass ${REDIS_PASSWORD}` 적용, healthcheck에도 반영. .env.example에 더미값 제공.
- PostgreSQL: 스펙 (A)안 — 단일 컨테이너 + init 스크립트로 6개 DB(auth/account/portfolio/trading/community/notification) 분리.

## 권고사항
- D8~D10 실행검증(`docker compose up -d`)은 이미지 다운로드(~GB)가 필요해 사용자 선택으로 남김.
- PASS 시 docs/09_개발환경.md를 인프라 접속표(localhost:5432/5433/6379/9092/27017)로 동기화 권고.
