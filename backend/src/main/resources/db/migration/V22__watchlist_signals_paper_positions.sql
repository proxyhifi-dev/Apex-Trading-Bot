CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(255) NOT NULL,
    exchange VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_watchlist_user_id ON watchlist (user_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'watchlist_user_symbol_exchange_key'
    ) THEN
        ALTER TABLE watchlist ADD CONSTRAINT watchlist_user_symbol_exchange_key UNIQUE (user_id, symbol, exchange);
    END IF;
END $$;

ALTER TABLE stock_screening_results ADD COLUMN IF NOT EXISTS score_breakdown TEXT;

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS qty INTEGER;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS avg_price NUMERIC(19,4);
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE paper_positions SET qty = quantity WHERE qty IS NULL;
UPDATE paper_positions SET avg_price = average_price WHERE avg_price IS NULL;
UPDATE paper_positions SET created_at = COALESCE(entry_time, now()) WHERE created_at IS NULL;
UPDATE paper_positions SET updated_at = COALESCE(exit_time, now()) WHERE updated_at IS NULL;

ALTER TABLE paper_positions ALTER COLUMN qty SET NOT NULL;
ALTER TABLE paper_positions ALTER COLUMN avg_price SET NOT NULL;
ALTER TABLE paper_positions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE paper_positions ALTER COLUMN updated_at SET NOT NULL;
