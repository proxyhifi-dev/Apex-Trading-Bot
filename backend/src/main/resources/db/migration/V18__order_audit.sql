CREATE TABLE IF NOT EXISTS order_audit (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    broker_order_id VARCHAR(100),
    paper_order_id VARCHAR(100),
    request_payload VARCHAR(4000),
    response_payload VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_order_audit_user_id ON order_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_order_audit_created_at ON order_audit(created_at);
