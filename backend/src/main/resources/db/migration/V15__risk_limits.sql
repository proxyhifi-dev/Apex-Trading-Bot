CREATE TABLE IF NOT EXISTS risk_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    daily_loss_limit NUMERIC(19,4),
    max_positions INT,
    max_consecutive_losses INT,
    portfolio_heat_limit NUMERIC(19,4),
    max_notional_exposure NUMERIC(19,4),
    max_symbol_exposure NUMERIC(19,4)
);
