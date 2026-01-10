ALTER TABLE trades ADD COLUMN IF NOT EXISTS exit_reason_detail VARCHAR(255);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS initial_risk_amount NUMERIC(19,4);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS current_r NUMERIC(19,4);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS max_favorable_r NUMERIC(19,4);
ALTER TABLE trades ADD COLUMN IF NOT EXISTS max_adverse_r NUMERIC(19,4);

ALTER TABLE paper_orders ADD COLUMN IF NOT EXISTS client_order_id VARCHAR(64);
UPDATE paper_orders SET client_order_id = order_id WHERE client_order_id IS NULL;
ALTER TABLE paper_orders ALTER COLUMN client_order_id SET NOT NULL;
ALTER TABLE paper_orders ADD CONSTRAINT paper_orders_client_order_id_unique UNIQUE (client_order_id);

CREATE TABLE IF NOT EXISTS market_regime_history (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    regime VARCHAR(32) NOT NULL,
    adx DOUBLE PRECISION NOT NULL,
    atr_percent DOUBLE PRECISION NOT NULL,
    detected_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS decision_audits (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    timeframe VARCHAR(16),
    decision_type VARCHAR(64) NOT NULL,
    decision_time TIMESTAMP NOT NULL,
    details TEXT
);

CREATE TABLE IF NOT EXISTS execution_costs (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64),
    client_order_id VARCHAR(64) NOT NULL UNIQUE,
    symbol VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    expected_cost NUMERIC(19,4),
    realized_cost NUMERIC(19,4),
    spread_cost NUMERIC(19,4),
    slippage_cost NUMERIC(19,4),
    commission_cost NUMERIC(19,4),
    tax_cost NUMERIC(19,4),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS circuit_breaker_state (
    id BIGINT PRIMARY KEY,
    global_halt BOOLEAN NOT NULL,
    entry_halt BOOLEAN NOT NULL,
    pause_until TIMESTAMP,
    reason VARCHAR(255),
    consecutive_losses INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS order_intents (
    id BIGSERIAL PRIMARY KEY,
    client_order_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS backtest_results (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(64) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    metrics_json TEXT,
    created_at TIMESTAMP NOT NULL
);
