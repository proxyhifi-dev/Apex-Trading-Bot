CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL UNIQUE,
    trading_symbol VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255),
    exchange VARCHAR(32),
    segment VARCHAR(32),
    tick_size NUMERIC(10,4),
    lot_size INTEGER,
    isin VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
