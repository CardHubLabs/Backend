ALTER TABLE favorites
    ADD COLUMN card_variant_id UUID REFERENCES card_variants(id);

ALTER TABLE price_alerts
    ADD COLUMN card_variant_id UUID REFERENCES card_variants(id);

UPDATE favorites f
   SET card_variant_id = l.card_variant_id
  FROM listings l
 WHERE f.listing_id = l.id;

UPDATE price_alerts pa
   SET card_variant_id = l.card_variant_id
  FROM listings l
 WHERE pa.listing_id = l.id;

DELETE FROM favorites duplicate
 USING (
     SELECT id
       FROM (
           SELECT id,
                  ROW_NUMBER() OVER (
                      PARTITION BY user_id, card_variant_id
                      ORDER BY created_at ASC, id ASC
                  ) AS row_number
             FROM favorites
       ) ranked
      WHERE ranked.row_number > 1
 ) losers
 WHERE duplicate.id = losers.id;

UPDATE price_alerts pa
   SET active = FALSE,
       updated_at = CURRENT_TIMESTAMP
  FROM (
      SELECT id
        FROM (
            SELECT id,
                   ROW_NUMBER() OVER (
                       PARTITION BY user_id, card_variant_id
                       ORDER BY created_at DESC, id DESC
                   ) AS row_number
              FROM price_alerts
             WHERE active = TRUE
        ) ranked
       WHERE ranked.row_number > 1
  ) losers
 WHERE pa.id = losers.id;

ALTER TABLE favorites
    ALTER COLUMN card_variant_id SET NOT NULL,
    ALTER COLUMN listing_id DROP NOT NULL;

ALTER TABLE price_alerts
    ALTER COLUMN card_variant_id SET NOT NULL,
    ALTER COLUMN listing_id DROP NOT NULL;

ALTER TABLE favorites DROP CONSTRAINT IF EXISTS uq_favorites_user_listing;
DROP INDEX IF EXISTS uq_price_alerts_active_user_listing;

CREATE UNIQUE INDEX uq_favorites_user_variant
    ON favorites(user_id, card_variant_id);

CREATE UNIQUE INDEX uq_price_alerts_active_user_variant
    ON price_alerts(user_id, card_variant_id)
    WHERE active = TRUE;

CREATE INDEX idx_favorites_variant_user
    ON favorites(card_variant_id, user_id);

CREATE INDEX idx_price_alerts_variant_active
    ON price_alerts(card_variant_id, created_at)
    WHERE active = TRUE;
