ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS sizing_multiplier NUMERIC(10,4);

CREATE TABLE IF NOT EXISTS system_guard_state (
    id BIGINT PRIMARY KEY,
    safe_mode BOOLEAN NOT NULL,
    last_reconcile_at TIMESTAMPTZ,
    last_mismatch_at TIMESTAMPTZ,
    last_mismatch_reason TEXT,
    updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS trading_guard_state (
    user_id BIGINT PRIMARY KEY,
    consecutive_losses INTEGER NOT NULL,
    last_loss_at TIMESTAMPTZ,
    cooldown_until TIMESTAMPTZ,
    trading_day_date DATE,
    day_pnl NUMERIC(19,4),
    updated_at TIMESTAMPTZ
);
