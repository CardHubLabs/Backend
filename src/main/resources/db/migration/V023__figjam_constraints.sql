ALTER TABLE card_variants
    ADD CONSTRAINT ck_card_variants_language CHECK (language IN ('KO', 'JA', 'EN'));

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_order_no_format CHECK (
        order_no ~ '^CH-[0-9a-f]{32}$'
        OR order_no ~ '^CH-L-[0-9a-f]{32}$'
    );

CREATE INDEX idx_shipments_carrier_code
    ON shipments(carrier_code);
