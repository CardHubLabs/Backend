CREATE TABLE upload_sessions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 10485760),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'ATTACHED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE listings (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES users(id),
    card_variant_id UUID NOT NULL REFERENCES card_variants(id),
    card_condition VARCHAR(40) NOT NULL CHECK (card_condition IN ('MINT', 'NEAR_MINT', 'LIGHT_PLAYED', 'MODERATELY_PLAYED', 'HEAVILY_PLAYED')),
    description TEXT,
    price BIGINT NOT NULL CHECK (price BETWEEN 500 AND 100000000),
    shipping_fee BIGINT NOT NULL CHECK (shipping_fee >= 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'HIDDEN', 'RESERVED', 'SOLD', 'DELETED')),
    reservation_order_id UUID,
    reservation_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_listings_reservation_pair CHECK (
        (status = 'RESERVED' AND reservation_order_id IS NOT NULL AND reservation_expires_at IS NOT NULL)
        OR
        (status <> 'RESERVED' AND reservation_order_id IS NULL AND reservation_expires_at IS NULL)
    )
);

CREATE TABLE listing_images (
    id UUID PRIMARY KEY,
    listing_id UUID NOT NULL REFERENCES listings(id),
    upload_session_id UUID NOT NULL UNIQUE REFERENCES upload_sessions(id),
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 5),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_listing_images_order UNIQUE (listing_id, sort_order)
);

CREATE INDEX idx_upload_sessions_owner_status ON upload_sessions(owner_id, status);
CREATE INDEX idx_upload_sessions_expiry ON upload_sessions(status, expires_at);
CREATE INDEX idx_listings_active_variant_price ON listings(card_variant_id, price, created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_listings_seller_created ON listings(seller_id, created_at DESC);
CREATE INDEX idx_listings_reservation_expiry ON listings(reservation_expires_at) WHERE status = 'RESERVED';
CREATE INDEX idx_listing_images_listing ON listing_images(listing_id, sort_order);
