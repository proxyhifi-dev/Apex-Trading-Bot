INSERT INTO instruments (symbol, trading_symbol, name, exchange, segment, tick_size, lot_size, isin)
VALUES
    ('INFY', 'NSE:INFY-EQ', 'Infosys', 'NSE', 'EQ', 0.05, 1, 'INE009A01021'),
    ('TCS', 'NSE:TCS-EQ', 'Tata Consultancy Services', 'NSE', 'EQ', 0.05, 1, 'INE467B01029'),
    ('RELIANCE', 'NSE:RELIANCE-EQ', 'Reliance Industries', 'NSE', 'EQ', 0.05, 1, 'INE002A01018'),
    ('NIFTY50', 'NSE:NIFTY50-INDEX', 'Nifty 50', 'NSE', 'INDEX', 0.05, 1, NULL),
    ('INDIAVIX', 'NSE:INDIAVIX-INDEX', 'India VIX', 'NSE', 'INDEX', 0.05, 1, NULL)
ON CONFLICT DO NOTHING;
