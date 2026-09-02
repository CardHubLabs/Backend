package com.kant.cardhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "card_variants")
public class CardVariant {

    @Id
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CardLanguage language;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(length = 100)
    private String rarity;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_type", nullable = false, length = 30)
    private VersionType versionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "print_type", nullable = false, length = 30)
    private PrintType printType;

    @Column(name = "is_listable", nullable = false)
    private boolean listable;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardVariant() {
    }

    private CardVariant(
            UUID id,
            UUID cardId,
            CardLanguage language,
            String version,
            String rarity,
            String imageUrl,
            VersionType versionType,
            PrintType printType,
            boolean listable,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.cardId = Objects.requireNonNull(cardId);
        this.language = Objects.requireNonNull(language, "language");
        this.version = requireText(version, "version");
        this.rarity = trimToNull(rarity);
        this.imageUrl = trimToNull(imageUrl);
        this.versionType = Objects.requireNonNull(versionType);
        this.printType = Objects.requireNonNull(printType);
        this.listable = listable;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static CardVariant create(UUID cardId, String language, String version, String rarity, String imageUrl, Instant now) {
        return create(
                cardId, language, version, rarity, imageUrl,
                VersionType.NORMAL, inferLegacyPrintType(version, rarity), true, now
        );
    }

    public static CardVariant create(
            UUID cardId,
            String language,
            String version,
            String rarity,
            String imageUrl,
            VersionType versionType,
            PrintType printType,
            boolean listable,
            Instant now
    ) {
        return create(
                cardId, CardLanguage.valueOf(requireText(language, "language").toUpperCase(java.util.Locale.ROOT)),
                version, rarity, imageUrl, versionType, printType, listable, now);
    }

    public static CardVariant create(
            UUID cardId,
            CardLanguage language,
            String version,
            String rarity,
            String imageUrl,
            VersionType versionType,
            PrintType printType,
            boolean listable,
            Instant now
    ) {
        return new CardVariant(
                UUID.randomUUID(), cardId, language, version, rarity, imageUrl,
                versionType, printType, listable, now
        );
    }

    public UUID getId() { return id; }
    public UUID getCardId() { return cardId; }
    public CardLanguage getLanguage() { return language; }
    public String getVersion() { return version; }
    public String getRarity() { return rarity; }
    public String getImageUrl() { return imageUrl; }
    public VersionType getVersionType() { return versionType; }
    public PrintType getPrintType() { return printType; }
    public boolean isListable() { return listable; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(
            String language,
            String version,
            String rarity,
            String imageUrl,
            VersionType versionType,
            PrintType printType,
            boolean listable,
            Instant now
    ) {
        update(CardLanguage.valueOf(requireText(language, "language").toUpperCase(java.util.Locale.ROOT)),
                version, rarity, imageUrl, versionType, printType, listable, now);
    }

    public void update(
            CardLanguage language,
            String version,
            String rarity,
            String imageUrl,
            VersionType versionType,
            PrintType printType,
            boolean listable,
            Instant now
    ) {
        this.language = Objects.requireNonNull(language, "language");
        this.version = requireText(version, "version");
        this.rarity = trimToNull(rarity);
        this.imageUrl = trimToNull(imageUrl);
        this.versionType = Objects.requireNonNull(versionType);
        this.printType = Objects.requireNonNull(printType);
        this.listable = listable;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void setListable(boolean listable, Instant now) {
        this.listable = listable;
        this.updatedAt = Objects.requireNonNull(now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PrintType inferLegacyPrintType(String version, String rarity) {
        String text = (version == null ? "" : version) + " " + (rarity == null ? "" : rarity);
        return text.toUpperCase(java.util.Locale.ROOT).contains("HOLO") ? PrintType.HOLO : PrintType.NORMAL;
    }
}
