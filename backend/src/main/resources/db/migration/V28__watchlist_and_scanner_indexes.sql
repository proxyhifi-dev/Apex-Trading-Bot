ALTER TABLE watchlist_items
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE watchlist_items
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);

UPDATE watchlist_items SET status = 'ACTIVE' WHERE status IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_watchlist_stocks_strategy_symbol
    ON watchlist_stocks (strategy_id, symbol);

CREATE INDEX IF NOT EXISTS idx_scanner_runs_user_status_created
    ON scanner_runs (user_id, status, created_at);
