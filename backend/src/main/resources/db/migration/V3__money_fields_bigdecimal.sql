ALTER TABLE users
    ALTER COLUMN available_funds TYPE NUMERIC(19,4) USING available_funds::NUMERIC(19,4),
    ALTER COLUMN total_invested TYPE NUMERIC(19,4) USING total_invested::NUMERIC(19,4),
    ALTER COLUMN current_value TYPE NUMERIC(19,4) USING current_value::NUMERIC(19,4);

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS user_id BIGINT,
    ALTER COLUMN entry_price TYPE NUMERIC(19,4) USING entry_price::NUMERIC(19,4),
    ALTER COLUMN exit_price TYPE NUMERIC(19,4) USING exit_price::NUMERIC(19,4),
    ALTER COLUMN stop_loss TYPE NUMERIC(19,4) USING stop_loss::NUMERIC(19,4),
    ALTER COLUMN current_stop_loss TYPE NUMERIC(19,4) USING current_stop_loss::NUMERIC(19,4),
    ALTER COLUMN atr TYPE NUMERIC(19,4) USING atr::NUMERIC(19,4),
    ALTER COLUMN highest_price TYPE NUMERIC(19,4) USING highest_price::NUMERIC(19,4),
    ALTER COLUMN realized_pnl TYPE NUMERIC(19,4) USING realized_pnl::NUMERIC(19,4);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM trades LIMIT 1) THEN
        UPDATE trades
        SET user_id = (SELECT id FROM users ORDER BY id ASC LIMIT 1)
        WHERE user_id IS NULL;
    END IF;
END $$;
ALTER TABLE trades ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE paper_trades
    ALTER COLUMN entry_price TYPE NUMERIC(19,4) USING entry_price::NUMERIC(19,4),
    ALTER COLUMN exit_price TYPE NUMERIC(19,4) USING exit_price::NUMERIC(19,4),
    ALTER COLUMN realized_pnl TYPE NUMERIC(19,4) USING realized_pnl::NUMERIC(19,4);

ALTER TABLE paper_positions
    ALTER COLUMN average_price TYPE NUMERIC(19,4) USING average_price::NUMERIC(19,4),
    ALTER COLUMN last_price TYPE NUMERIC(19,4) USING last_price::NUMERIC(19,4),
    ALTER COLUMN unrealized_pnl TYPE NUMERIC(19,4) USING unrealized_pnl::NUMERIC(19,4);

ALTER TABLE paper_orders
    ALTER COLUMN price TYPE NUMERIC(19,4) USING price::NUMERIC(19,4);

ALTER TABLE paper_portfolio_stats
    ALTER COLUMN net_pnl TYPE NUMERIC(19,4) USING net_pnl::NUMERIC(19,4);

ALTER TABLE portfolio_metrics
    ALTER COLUMN total_equity TYPE NUMERIC(19,4) USING total_equity::NUMERIC(19,4),
    ALTER COLUMN available_balance TYPE NUMERIC(19,4) USING available_balance::NUMERIC(19,4),
    ALTER COLUMN day_pnl TYPE NUMERIC(19,4) USING day_pnl::NUMERIC(19,4),
    ALTER COLUMN month_pnl TYPE NUMERIC(19,4) USING month_pnl::NUMERIC(19,4);

ALTER TABLE risk_metrics
    ALTER COLUMN daily_loss TYPE NUMERIC(19,4) USING daily_loss::NUMERIC(19,4),
    ALTER COLUMN current_equity TYPE NUMERIC(19,4) USING current_equity::NUMERIC(19,4);

ALTER TABLE stock_screening_results
    ALTER COLUMN entry_price TYPE NUMERIC(19,4) USING entry_price::NUMERIC(19,4),
    ALTER COLUMN stop_loss TYPE NUMERIC(19,4) USING stop_loss::NUMERIC(19,4);

ALTER TABLE circuit_breaker_logs
    ALTER COLUMN triggered_value TYPE NUMERIC(19,4) USING triggered_value::NUMERIC(19,4);

ALTER TABLE trading_strategies
    ALTER COLUMN initial_capital TYPE NUMERIC(19,4) USING initial_capital::NUMERIC(19,4);
