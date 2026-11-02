CREATE TABLE trades (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    order_item_id UUID NOT NULL UNIQUE REFERENCES order_items(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    card_variant_id UUID NOT NULL REFERENCES card_variants(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    price BIGINT NOT NULL CHECK (price >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    excluded BOOLEAN NOT NULL DEFAULT FALSE,
    exclusion_reason VARCHAR(100),
    excluded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_trades_exclusion CHECK (
        (excluded = FALSE AND exclusion_reason IS NULL AND excluded_at IS NULL)
        OR (excluded = TRUE AND exclusion_reason IS NOT NULL AND excluded_at IS NOT NULL)
    )
);

CREATE TABLE market_price_stats (
    card_variant_id UUID PRIMARY KEY REFERENCES card_variants(id),
    recent_price BIGINT,
    average_7d BIGINT,
    median_30d BIGINT,
    active_lowest_price BIGINT,
    volume_30d BIGINT NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tags_json TEXT NOT NULL DEFAULT '[]',
    content VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trades_variant_occurred
    ON trades(card_variant_id, occurred_at DESC) WHERE excluded = FALSE;
CREATE INDEX idx_trades_order ON trades(order_id);
CREATE INDEX idx_reviews_seller_created ON reviews(seller_id, created_at DESC);

