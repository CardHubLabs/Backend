CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    seller_id UUID NOT NULL REFERENCES users(id),
    carrier_code VARCHAR(40) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('READY', 'IN_TRANSIT', 'DELIVERED', 'EXCEPTION')),
    delivered_at TIMESTAMPTZ,
    auto_confirm_held BOOLEAN NOT NULL DEFAULT FALSE,
    auto_confirm_notice_sent_at TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    next_check_at TIMESTAMPTZ NOT NULL,
    consecutive_failures INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipments_tracking UNIQUE (carrier_code, tracking_number),
    CONSTRAINT ck_shipments_delivered_at CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR status <> 'DELIVERED'
    )
);

CREATE TABLE shipment_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id),
    provider_event_key VARCHAR(200),
    status VARCHAR(24) NOT NULL CHECK (status IN ('READY', 'IN_TRANSIT', 'DELIVERED', 'EXCEPTION')),
    description VARCHAR(500),
    event_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_shipment_events_provider UNIQUE (shipment_id, provider_event_key)
);

CREATE INDEX idx_shipments_tracking_due ON shipments(next_check_at, id)
    WHERE status <> 'DELIVERED';
CREATE INDEX idx_shipments_auto_confirm ON shipments(delivered_at, id)
    WHERE status = 'DELIVERED';
CREATE INDEX idx_shipment_events_timeline ON shipment_events(shipment_id, event_at DESC);

