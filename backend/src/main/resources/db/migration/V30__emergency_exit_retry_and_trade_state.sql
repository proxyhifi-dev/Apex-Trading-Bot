ALTER TABLE system_guard_state
    ADD COLUMN IF NOT EXISTS emergency_mode BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS emergency_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS emergency_started_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS exit_retry_requests (
    id BIGSERIAL PRIMARY KEY,
    trade_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    side VARCHAR(10) NOT NULL,
    paper BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    next_attempt_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    reason VARCHAR(255),
    last_error TEXT,
    dlq_logged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

UPDATE trades SET position_state = 'OPENING' WHERE position_state = 'PLANNED';
UPDATE trades SET position_state = 'CLOSING' WHERE position_state = 'EXITING';
