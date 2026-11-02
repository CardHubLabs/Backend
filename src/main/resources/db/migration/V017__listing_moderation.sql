ALTER TABLE listings
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'CLEAR',
    ADD COLUMN blocked_at TIMESTAMPTZ,
    ADD COLUMN blocked_by UUID,
    ADD COLUMN blocked_reason VARCHAR(500);

ALTER TABLE listings
    ADD CONSTRAINT fk_listings_blocked_by
        FOREIGN KEY (blocked_by) REFERENCES users(id),
    ADD CONSTRAINT ck_listings_moderation_status
        CHECK (moderation_status IN ('CLEAR', 'BLOCKED')),
    ADD CONSTRAINT ck_listings_moderation_details
        CHECK (
            (
                moderation_status = 'CLEAR'
                AND blocked_at IS NULL
                AND blocked_by IS NULL
                AND blocked_reason IS NULL
            )
            OR
            (
                moderation_status = 'BLOCKED'
                AND blocked_at IS NOT NULL
                AND blocked_by IS NOT NULL
                AND blocked_reason IS NOT NULL
                AND BTRIM(blocked_reason) <> ''
                AND status IN ('HIDDEN', 'RESERVED', 'SOLD', 'DELETED')
            )
        );

CREATE INDEX idx_listings_blocked_at
    ON listings(blocked_at DESC, id)
    WHERE moderation_status = 'BLOCKED';
