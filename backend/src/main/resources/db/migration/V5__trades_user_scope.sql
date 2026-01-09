ALTER TABLE trades ADD COLUMN IF NOT EXISTS user_id BIGINT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM trades LIMIT 1) THEN
        UPDATE trades
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;

ALTER TABLE trades ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trades_user_time
    ON trades (user_id, entry_time);

CREATE INDEX IF NOT EXISTS idx_trades_user_mode_status
    ON trades (user_id, is_paper_trade, status);
