ALTER TABLE orders
    ADD COLUMN order_no VARCHAR(48),
    ADD COLUMN shipping_address_schema_version SMALLINT NOT NULL DEFAULT 1;

UPDATE orders
   SET order_no = 'CH-L-' || REPLACE(id::text, '-', '')
 WHERE order_no IS NULL;

ALTER TABLE orders
    ALTER COLUMN order_no SET NOT NULL,
    ADD CONSTRAINT uk_orders_order_no UNIQUE (order_no),
    ADD CONSTRAINT ck_orders_shipping_address_schema_version
        CHECK (shipping_address_schema_version IN (1, 2));

ALTER TABLE order_items
    ADD COLUMN source_offer_id UUID REFERENCES offers(id),
    ADD COLUMN card_condition_snapshot VARCHAR(40),
    ADD COLUMN shipping_fee_snapshot BIGINT,
    ADD COLUMN primary_image_storage_key VARCHAR(500);

UPDATE order_items oi
   SET card_condition_snapshot = l.card_condition,
       shipping_fee_snapshot = l.shipping_fee,
       primary_image_storage_key = image.storage_key
  FROM listings l
  LEFT JOIN LATERAL (
      SELECT li.storage_key
        FROM listing_images li
       WHERE li.listing_id = l.id
       ORDER BY li.sort_order, li.id
       LIMIT 1
  ) image ON TRUE
 WHERE oi.listing_id = l.id;

ALTER TABLE order_items
    ALTER COLUMN card_condition_snapshot SET NOT NULL,
    ALTER COLUMN shipping_fee_snapshot SET NOT NULL,
    ADD CONSTRAINT ck_order_items_shipping_fee_snapshot CHECK (shipping_fee_snapshot >= 0),
    ADD CONSTRAINT ck_order_items_card_condition_snapshot CHECK (card_condition_snapshot IN (
        'MINT', 'NEAR_MINT', 'LIGHT_PLAYED', 'MODERATELY_PLAYED', 'HEAVILY_PLAYED'
    ));

CREATE UNIQUE INDEX uk_order_items_source_offer
    ON order_items(source_offer_id)
    WHERE source_offer_id IS NOT NULL;

CREATE INDEX idx_orders_order_no ON orders(order_no);
