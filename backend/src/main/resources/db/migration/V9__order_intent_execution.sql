ALTER TABLE order_intents
    ADD COLUMN broker_order_id VARCHAR(255),
    ADD COLUMN filled_quantity INTEGER,
    ADD COLUMN average_price NUMERIC(19, 4),
    ADD COLUMN updated_at TIMESTAMP;
