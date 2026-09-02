package com.kant.cardhub.catalog.domain;

import java.util.Locale;

public enum GameCode {
    POKEMON,
    ONE_PIECE;

    public static GameCode fromLegacySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pokemon", "pokemon-tcg" -> POKEMON;
            case "one-piece", "one-piece-tcg", "onepiece" -> ONE_PIECE;
            default -> throw new IllegalArgumentException("Unsupported card game slug: " + slug);
        };
    }
}
