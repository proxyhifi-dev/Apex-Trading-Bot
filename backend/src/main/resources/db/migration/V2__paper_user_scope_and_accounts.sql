CREATE TABLE IF NOT EXISTS paper_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    starting_capital NUMERIC(19,4) NOT NULL,
    cash_balance NUMERIC(19,4) NOT NULL,
    reserved_margin NUMERIC(19,4) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(19,4) NOT NULL DEFAULT 0,
    unrealized_pnl NUMERIC(19,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'paper_accounts_user_id_key'
    ) THEN
        ALTER TABLE paper_accounts ADD CONSTRAINT paper_accounts_user_id_key UNIQUE (user_id);
    END IF;
END $$;

ALTER TABLE settings ADD COLUMN IF NOT EXISTS user_id BIGINT;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM settings LIMIT 1) THEN
        UPDATE settings
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;
ALTER TABLE settings ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE paper_trades ADD COLUMN IF NOT EXISTS user_id BIGINT;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM paper_trades LIMIT 1) THEN
        UPDATE paper_trades
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;
ALTER TABLE paper_trades ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE paper_positions ADD COLUMN IF NOT EXISTS user_id BIGINT;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM paper_positions LIMIT 1) THEN
        UPDATE paper_positions
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;
ALTER TABLE paper_positions ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE paper_orders ADD COLUMN IF NOT EXISTS user_id BIGINT;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM paper_orders LIMIT 1) THEN
        UPDATE paper_orders
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;
ALTER TABLE paper_orders ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE paper_portfolio_stats ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE paper_portfolio_stats ADD COLUMN IF NOT EXISTS date DATE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM paper_portfolio_stats LIMIT 1) THEN
        UPDATE paper_portfolio_stats
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
        UPDATE paper_portfolio_stats
        SET date = CURRENT_DATE
        WHERE date IS NULL;
    END IF;
END $$;
ALTER TABLE paper_portfolio_stats ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE paper_portfolio_stats ALTER COLUMN date SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_paper_trades_user_created_at ON paper_trades (user_id, entry_time);
CREATE INDEX IF NOT EXISTS idx_paper_trades_user_symbol_created_at ON paper_trades (user_id, symbol, entry_time);
CREATE INDEX IF NOT EXISTS idx_paper_positions_user_symbol ON paper_positions (user_id, symbol);
CREATE INDEX IF NOT EXISTS idx_paper_orders_user_created_at ON paper_orders (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_paper_portfolio_stats_user_date ON paper_portfolio_stats (user_id, date);
CREATE INDEX IF NOT EXISTS idx_settings_user_id ON settings (user_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'settings_user_id_key'
    ) THEN
        ALTER TABLE settings ADD CONSTRAINT settings_user_id_key UNIQUE (user_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'paper_positions_user_symbol_key'
    ) THEN
        ALTER TABLE paper_positions ADD CONSTRAINT paper_positions_user_symbol_key UNIQUE (user_id, symbol);
    END IF;
END $$;
