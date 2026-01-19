CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_payload TEXT,
    error_message TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_idempotency_unique
    ON idempotency_keys(user_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_created
    ON idempotency_keys(created_at);
