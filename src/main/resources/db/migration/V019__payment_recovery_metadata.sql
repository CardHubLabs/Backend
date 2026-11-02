ALTER TABLE payments
    ADD COLUMN provider_status VARCHAR(100),
    ADD COLUMN payment_method VARCHAR(24),
    ADD COLUMN easy_pay_provider VARCHAR(100),
    ADD COLUMN confirm_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_confirm_attempt_at TIMESTAMPTZ,
    ADD COLUMN next_reconcile_at TIMESTAMPTZ,
    ADD COLUMN manual_review_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_payment_method
        CHECK (payment_method IS NULL OR payment_method IN ('CARD', 'EASY_PAY')),
    ADD CONSTRAINT ck_payments_confirm_attempt_count
        CHECK (confirm_attempt_count >= 0);

DROP INDEX IF EXISTS idx_payments_unknown_reconcile;
CREATE INDEX idx_payments_unknown_reconcile
    ON payments(next_reconcile_at, last_reconciled_at, created_at)
    WHERE status IN ('UNKNOWN', 'CONFIRMING') AND manual_review_required = FALSE;

CREATE INDEX idx_payments_manual_review
    ON payments(updated_at DESC)
    WHERE manual_review_required = TRUE;
