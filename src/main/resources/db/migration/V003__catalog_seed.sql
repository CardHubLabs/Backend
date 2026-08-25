INSERT INTO card_games (id, name, slug, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'Pokemon Trading Card Game', 'pokemon-tcg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO card_sets (id, game_id, name, code, released_at, created_at, updated_at)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'Base Set',
    'BASE1',
    DATE '1999-01-09',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

INSERT INTO cards (id, card_set_id, name, card_number, created_at, updated_at)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Charizard',
    '4/102',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

INSERT INTO card_variants (id, card_id, language, version, rarity, image_url, created_at, updated_at)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    'EN',
    'UNLIMITED',
    'RARE_HOLO',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;
