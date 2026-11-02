CREATE INDEX idx_favorites_user_created
    ON favorites(user_id, created_at DESC, id DESC);

CREATE INDEX idx_price_alerts_listing_active
    ON price_alerts(listing_id, active, created_at);

CREATE INDEX idx_price_alerts_user_active
    ON price_alerts(user_id, active, created_at DESC);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE INDEX idx_scheduled_jobs_due
    ON scheduled_jobs(available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_scheduled_jobs_stale
    ON scheduled_jobs(locked_at)
    WHERE status = 'RUNNING';
