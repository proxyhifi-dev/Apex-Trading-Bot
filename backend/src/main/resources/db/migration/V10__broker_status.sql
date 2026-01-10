CREATE TABLE broker_status (
    id BIGSERIAL PRIMARY KEY,
    broker VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    reason TEXT,
    degraded_at TIMESTAMP,
    updated_at TIMESTAMP
);
