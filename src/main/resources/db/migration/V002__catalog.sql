CREATE TABLE card_games (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE card_sets (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES card_games(id),
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    released_at DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_card_sets_game_code UNIQUE (game_id, code)
);

CREATE TABLE cards (
    id UUID PRIMARY KEY,
    card_set_id UUID NOT NULL REFERENCES card_sets(id),
    name VARCHAR(200) NOT NULL,
    card_number VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cards_set_number UNIQUE (card_set_id, card_number)
);

CREATE TABLE card_variants (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL REFERENCES cards(id),
    language VARCHAR(30) NOT NULL,
    version VARCHAR(100) NOT NULL,
    rarity VARCHAR(100),
    image_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_card_variants_card_language_version UNIQUE (card_id, language, version)
);

CREATE INDEX idx_card_sets_game_id ON card_sets(game_id);
CREATE INDEX idx_cards_set_id ON cards(card_set_id);
CREATE INDEX idx_cards_name_lower ON cards(LOWER(name));
CREATE INDEX idx_card_variants_card_id ON card_variants(card_id);
