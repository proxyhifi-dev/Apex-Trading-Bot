ALTER TABLE stock_screening_results ADD COLUMN IF NOT EXISTS user_id BIGINT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_screening_results LIMIT 1) THEN
        UPDATE stock_screening_results
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;

ALTER TABLE stock_screening_results ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_screening_results_user_scan_time
    ON stock_screening_results (user_id, scan_time);
