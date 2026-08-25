package com.kant.cardhub.catalog.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogView {
    private CatalogView() {
    }

    public record Game(UUID id, String name, String slug) {}
    public record Set(UUID id, UUID gameId, String name, String code, LocalDate releasedAt) {}
    public record CardSummary(UUID id, UUID cardSetId, String name, String cardNumber) {}
    public record Variant(UUID id, String language, String version, String rarity, String imageUrl) {}
    public record CardDetail(
            UUID id,
            UUID cardSetId,
            String name,
            String cardNumber,
            List<Variant> variants
    ) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
