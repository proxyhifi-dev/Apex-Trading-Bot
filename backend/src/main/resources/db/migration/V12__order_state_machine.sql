-- Migration: Order State Machine and Position State
-- Adds state machine fields to order_intents and trades tables

-- Add state machine fields to order_intents
ALTER TABLE order_intents 
    ADD COLUMN IF NOT EXISTS order_state VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN IF NOT EXISTS last_broker_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS acked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS signal_id BIGINT;

-- Add position state fields to trades
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS position_state VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN IF NOT EXISTS stop_order_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stop_order_state VARCHAR(50),
    ADD COLUMN IF NOT EXISTS stop_acked_at TIMESTAMP;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_order_intents_state ON order_intents(order_state);
CREATE INDEX IF NOT EXISTS idx_order_intents_expires ON order_intents(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_position_state ON trades(position_state);
CREATE INDEX IF NOT EXISTS idx_order_intents_signal_id ON order_intents(signal_id) WHERE signal_id IS NOT NULL;

-- Migrate existing status values to order_state
UPDATE order_intents 
SET order_state = CASE 
    WHEN status = 'PENDING' THEN 'CREATED'
    WHEN status = 'PLACED' THEN 'SENT'
    WHEN status = 'FILLED' THEN 'FILLED'
    WHEN status = 'REJECTED' THEN 'REJECTED'
    WHEN status = 'CANCELLED' THEN 'CANCELLED'
    ELSE 'UNKNOWN'
END
WHERE order_state = 'CREATED';

-- Migrate existing trade status to position_state
UPDATE trades
SET position_state = CASE
    WHEN status = 'OPEN' THEN 'OPEN'
    WHEN status = 'CLOSED' THEN 'CLOSED'
    ELSE 'PLANNED'
END
WHERE position_state = 'PLANNED';
