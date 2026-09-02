package com.kant.cardhub.catalog.application;

import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.catalog.domain.CardLanguage;
import com.kant.cardhub.catalog.domain.PrintType;
import com.kant.cardhub.catalog.domain.VersionType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogView {
    private CatalogView() {
    }

    public record Game(UUID id, String name, String slug, GameCode code) {}
    public record Set(UUID id, UUID gameId, String name, String code, LocalDate releasedAt) {}
    public record CardSummary(
            UUID id,
            UUID cardSetId,
            String name,
            String cardNumber,
            String nameKo,
            String nameEn,
            String nameJa,
            String rarity
    ) {}
    public record Variant(
            UUID id,
            CardLanguage language,
            String version,
            String rarity,
            String imageUrl,
            UUID cardId,
            VersionType versionType,
            PrintType printType,
            boolean listable
    ) {}
    public record CardDetail(
            UUID id,
            UUID cardSetId,
            String name,
            String cardNumber,
            String nameKo,
            String nameEn,
            String nameJa,
            String rarity,
            List<Variant> variants
    ) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
