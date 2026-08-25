CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    item_subtotal BIGINT NOT NULL CHECK (item_subtotal >= 0),
    shipping_fee BIGINT NOT NULL CHECK (shipping_fee >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount = item_subtotal + shipping_fee),
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'PAYMENT_PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CONFIRMED',
        'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED'
    )),
    payment_in_flight BOOLEAN NOT NULL DEFAULT FALSE,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    shipping_address_ciphertext TEXT NOT NULL,
    shipping_address_key_version VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_participants_different CHECK (buyer_id <> seller_id)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    card_variant_id UUID NOT NULL REFERENCES card_variants(id),
    listing_title VARCHAR(200) NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_order_items_order_listing UNIQUE (order_id, listing_id)
);

ALTER TABLE listings
    ADD CONSTRAINT fk_listings_reservation_order
    FOREIGN KEY (reservation_order_id) REFERENCES orders(id);

CREATE INDEX idx_orders_buyer_created ON orders(buyer_id, created_at DESC);
CREATE INDEX idx_orders_seller_created ON orders(seller_id, created_at DESC);
CREATE INDEX idx_orders_expirable
    ON orders(reservation_expires_at, id)
    WHERE status = 'PAYMENT_PENDING' AND payment_in_flight = FALSE;
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_variant ON order_items(card_variant_id);

