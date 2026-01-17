CREATE TABLE IF NOT EXISTS bot_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    running BOOLEAN NOT NULL DEFAULT false,
    last_scan_at TIMESTAMP WITH TIME ZONE,
    next_scan_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    last_error_at TIMESTAMP WITH TIME ZONE,
    thread_alive BOOLEAN,
    queue_depth INT
);
