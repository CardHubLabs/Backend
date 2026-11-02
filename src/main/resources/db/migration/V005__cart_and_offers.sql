CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cart_items_user_listing UNIQUE (user_id, listing_id)
);

CREATE TABLE offers (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    offered_price BIGINT NOT NULL CHECK (offered_price >= 500),
    counter_price BIGINT CHECK (counter_price IS NULL OR counter_price >= 500),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'COUNTERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONSUMED')),
    counter_count INTEGER NOT NULL DEFAULT 0 CHECK (counter_count BETWEEN 0 AND 1),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE offer_actions (
    id UUID PRIMARY KEY,
    offer_id UUID NOT NULL REFERENCES offers(id),
    actor_id UUID REFERENCES users(id),
    action_type VARCHAR(30) NOT NULL CHECK (action_type IN ('CREATED', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'EXPIRED', 'CONSUMED')),
    price BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cart_items_user_created ON cart_items(user_id, created_at DESC);
CREATE INDEX idx_offers_buyer_created ON offers(buyer_id, created_at DESC);
CREATE INDEX idx_offers_seller_created ON offers(seller_id, created_at DESC);
CREATE INDEX idx_offers_expiry ON offers(expires_at) WHERE status IN ('PENDING', 'COUNTERED', 'ACCEPTED');
CREATE INDEX idx_offer_actions_offer_created ON offer_actions(offer_id, created_at);

CREATE UNIQUE INDEX uk_offers_open_buyer_listing
    ON offers(listing_id, buyer_id)
    WHERE status IN ('PENDING', 'COUNTERED', 'ACCEPTED');

CREATE UNIQUE INDEX uk_offers_one_accepted_per_listing
    ON offers(listing_id)
    WHERE status = 'ACCEPTED';
