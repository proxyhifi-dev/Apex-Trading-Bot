CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    description VARCHAR(512),
    metadata TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_events_created
    ON audit_events(created_at);

CREATE INDEX IF NOT EXISTS idx_audit_events_user
    ON audit_events(user_id);
