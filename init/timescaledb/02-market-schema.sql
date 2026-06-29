-- StockPulse TimescaleDB 시세 스키마 (Phase 1)
-- ticks 하이퍼테이블 + candles_1m 연속 집계

CREATE TABLE IF NOT EXISTS ticks (
    time   TIMESTAMPTZ   NOT NULL,
    symbol VARCHAR(20)   NOT NULL,
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
