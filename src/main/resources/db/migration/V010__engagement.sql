CREATE TABLE favorites (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_favorites_user_listing UNIQUE (user_id, listing_id)
);

CREATE TABLE price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    baseline_price BIGINT NOT NULL CHECK (baseline_price >= 0),
    threshold_percentage INTEGER NOT NULL DEFAULT 10
        CHECK (threshold_percentage BETWEEN 1 AND 99),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    triggered_price BIGINT,
    triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_price_alert_trigger_pair CHECK (
        (triggered_at IS NULL AND triggered_price IS NULL)
        OR (triggered_at IS NOT NULL AND triggered_price IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_price_alerts_active_user_listing
    ON price_alerts(user_id, listing_id)
    WHERE active = TRUE;

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(40) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40),
    reference_id UUID,
    dedupe_key VARCHAR(200),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notifications_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'PAYMENT_DONE', 'SALE_MADE', 'SHIPPED', 'CONFIRMED',
        'AUTO_CONFIRM_NOTICE', 'OFFER_REPLY', 'OFFER_EXPIRED',
        'PRICE_ALERT', 'LISTING_ALERT', 'UNDERCUT'
    ))
);
