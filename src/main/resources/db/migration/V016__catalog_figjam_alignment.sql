ALTER TABLE card_games
    ADD COLUMN code VARCHAR(30);

UPDATE card_games
   SET code = CASE
       WHEN LOWER(slug) IN ('pokemon', 'pokemon-tcg') THEN 'POKEMON'
       WHEN LOWER(slug) IN ('one-piece', 'one-piece-tcg', 'onepiece') THEN 'ONE_PIECE'
       ELSE NULL
   END;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM card_games WHERE code IS NULL) THEN
        RAISE EXCEPTION 'Every legacy card game must map to POKEMON or ONE_PIECE before V016';
    END IF;
END $$;

ALTER TABLE card_games
    ALTER COLUMN code SET NOT NULL,
    ADD CONSTRAINT uq_card_games_code UNIQUE (code),
    ADD CONSTRAINT ck_card_games_code CHECK (code IN ('POKEMON', 'ONE_PIECE'));

INSERT INTO card_games(id, name, slug, code, created_at, updated_at)
VALUES (
    '10000000-0000-0000-0000-000000000002',
    'One Piece Card Game',
    'one-piece-tcg',
    'ONE_PIECE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

ALTER TABLE cards
    ADD COLUMN name_ko VARCHAR(200),
    ADD COLUMN name_en VARCHAR(200),
    ADD COLUMN name_ja VARCHAR(200),
    ADD COLUMN rarity VARCHAR(100);

UPDATE cards
   SET name_en = name
 WHERE name_en IS NULL;

ALTER TABLE card_variants
    ADD COLUMN version_type VARCHAR(30),
    ADD COLUMN print_type VARCHAR(30),
    ADD COLUMN is_listable BOOLEAN;

UPDATE card_variants
   SET version_type = CASE
           WHEN UPPER(version) LIKE '%MANGA%' THEN 'MANGA'
           WHEN UPPER(version) LIKE '%PROMO%' THEN 'PROMO'
           ELSE 'NORMAL'
       END,
       print_type = CASE
           WHEN UPPER(COALESCE(version, '') || ' ' || COALESCE(rarity, '')) LIKE '%HOLO%' THEN 'HOLO'
           ELSE 'NORMAL'
       END,
       is_listable = TRUE;

ALTER TABLE card_variants
    ALTER COLUMN version_type SET NOT NULL,
    ALTER COLUMN print_type SET NOT NULL,
    ALTER COLUMN is_listable SET NOT NULL,
    ALTER COLUMN is_listable SET DEFAULT TRUE,
    ADD CONSTRAINT ck_card_variants_version_type
        CHECK (version_type IN ('NORMAL', 'PROMO', 'MANGA')),
    ADD CONSTRAINT ck_card_variants_print_type
        CHECK (print_type IN ('NORMAL', 'HOLO'));

CREATE INDEX idx_card_variants_catalog_filter
    ON card_variants(version_type, print_type, is_listable, language, card_id);
