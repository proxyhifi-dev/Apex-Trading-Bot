CREATE TABLE IF NOT EXISTS watchlists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS watchlist_items (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    symbol VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_watchlists_user_id ON watchlists (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_watchlists_user_name ON watchlists (user_id, name);
CREATE INDEX IF NOT EXISTS idx_watchlist_items_watchlist ON watchlist_items (watchlist_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_watchlist_items_watchlist_symbol ON watchlist_items (watchlist_id, symbol);

INSERT INTO watchlists (user_id, name, is_default, created_at, updated_at)
SELECT DISTINCT user_id, 'Default', true, now(), now()
FROM watchlist
ON CONFLICT DO NOTHING;

INSERT INTO watchlist_items (watchlist_id, symbol, created_at)
SELECT w.id,
       UPPER(wl.exchange || ':' || wl.symbol),
       wl.created_at
FROM watchlist wl
JOIN watchlists w ON w.user_id = wl.user_id AND w.is_default = true
ON CONFLICT DO NOTHING;
