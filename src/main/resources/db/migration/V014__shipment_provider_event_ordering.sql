ALTER TABLE shipments
    ADD COLUMN last_provider_event_at TIMESTAMPTZ;

-- Historical non-terminal events cannot be reliably tied to the currently registered
-- tracking number, so their ordering cursor intentionally starts fresh. Delivered is
-- terminal and its authoritative provider timestamp is already stored on the shipment.
UPDATE shipments
   SET last_provider_event_at = delivered_at
 WHERE status = 'DELIVERED';
