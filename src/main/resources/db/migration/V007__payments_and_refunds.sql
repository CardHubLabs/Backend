CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    payment_key VARCHAR(200) NOT NULL UNIQUE,
    toss_order_id VARCHAR(64) NOT NULL UNIQUE,
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(24) NOT NULL CHECK (status IN ('CONFIRMING', 'APPROVED', 'FAILED', 'UNKNOWN')),
    provider_code VARCHAR(100),
    approved_at TIMESTAMPTZ,
    last_reconciled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('REQUESTED', 'PROCESSING', 'APPROVED', 'FAILED', 'UNKNOWN')),
    provider_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_unknown_reconcile
    ON payments(last_reconciled_at, created_at)
    WHERE status = 'UNKNOWN';
CREATE INDEX idx_refunds_status_updated ON refunds(status, updated_at);

