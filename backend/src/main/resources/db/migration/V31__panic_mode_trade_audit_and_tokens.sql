ALTER TABLE system_guard_state
    ADD COLUMN IF NOT EXISTS panic_mode BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS panic_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS panic_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS system_mode VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    ADD COLUMN IF NOT EXISTS last_panic_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_panic_at TIMESTAMP;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS fyers_token_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE broker_status
    ADD COLUMN IF NOT EXISTS next_allowed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS trade_state_audits (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    reason VARCHAR(100),
    detail TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

UPDATE system_guard_state
SET system_mode = CASE
    WHEN emergency_mode THEN 'PANIC'
    ELSE system_mode
END
WHERE system_mode IS NULL;
