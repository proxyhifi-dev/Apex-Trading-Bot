CREATE TABLE IF NOT EXISTS scanner_runs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    universe_type VARCHAR(40) NOT NULL,
    universe_payload TEXT,
    strategy_id VARCHAR(200),
    options_payload TEXT,
    dry_run BOOLEAN NOT NULL DEFAULT false,
    mode VARCHAR(20) NOT NULL DEFAULT 'PAPER',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    total_symbols INTEGER,
    passed_stage1 INTEGER,
    passed_stage2 INTEGER,
    final_signals INTEGER,
    rejected_stage1_reason_counts TEXT,
    rejected_stage2_reason_counts TEXT
);

CREATE TABLE IF NOT EXISTS scanner_run_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES scanner_runs(id) ON DELETE CASCADE,
    symbol VARCHAR(32) NOT NULL,
    score DOUBLE PRECISION,
    grade VARCHAR(10),
    entry_price DOUBLE PRECISION,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_scanner_runs_user_id ON scanner_runs (user_id);
CREATE INDEX IF NOT EXISTS idx_scanner_run_results_run_id ON scanner_run_results (run_id);
