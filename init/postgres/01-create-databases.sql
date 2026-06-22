-- StockPulse PostgreSQL 초기화 (WBS 0-2)
-- 컨테이너 최초 기동 시 /docker-entrypoint-initdb.d/ 에서 자동 실행됨.
-- "DB per service" 정신 유지: 단일 컨테이너 안에 서비스별 database 6개를 논리적으로 분리.
-- 소유자는 부팅 시 생성되는 POSTGRES_USER. 테이블 DDL은 넣지 않는다(각 서비스 마이그레이션 책임).

CREATE DATABASE auth;
CREATE DATABASE account;
CREATE DATABASE portfolio;
CREATE DATABASE trading;
CREATE DATABASE community;
CREATE DATABASE notification;
