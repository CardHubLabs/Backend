ALTER TABLE market_price_stats
    ADD COLUMN median_7d BIGINT,
    ADD COLUMN average_30d BIGINT,
    ADD COLUMN average_90d BIGINT,
    ADD COLUMN median_90d BIGINT;
