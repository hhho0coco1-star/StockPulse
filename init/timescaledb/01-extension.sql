-- StockPulse TimescaleDB 초기화 (WBS 0-2)
-- 컨테이너 최초 기동 시 /docker-entrypoint-initdb.d/ 에서 자동 실행됨.
-- timescaledb 확장만 보장한다. 하이퍼테이블(ticks)·연속집계(candles) DDL은
-- Market Collector 서비스(Phase 1)의 마이그레이션 책임이므로 여기 넣지 않는다.

CREATE EXTENSION IF NOT EXISTS timescaledb;
