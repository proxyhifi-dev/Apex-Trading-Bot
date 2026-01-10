ALTER TABLE backtest_results ADD COLUMN IF NOT EXISTS user_id BIGINT;
UPDATE backtest_results SET user_id = 0 WHERE user_id IS NULL;
ALTER TABLE backtest_results ALTER COLUMN user_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS validation_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    backtest_result_id BIGINT NOT NULL,
    metrics_json VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS trade_feature_attribution (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    feature VARCHAR(50) NOT NULL,
    normalized_value DOUBLE PRECISION NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    contribution DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS correlation_regime_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    regime VARCHAR(20) NOT NULL,
    avg_off_diagonal_correlation DOUBLE PRECISION NOT NULL,
    sizing_multiplier DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS correlation_matrix_detailed (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    symbol_a VARCHAR(50) NOT NULL,
    symbol_b VARCHAR(50) NOT NULL,
    correlation DOUBLE PRECISION NOT NULL,
    calculated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS strategy_health_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    paused BOOLEAN NOT NULL,
    reasons VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
