package com.kant.cardhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false, length = 30)
    private String language;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(length = 100)
    private String rarity;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardVariant() {
    }

    private CardVariant(UUID id, UUID cardId, String language, String version, String rarity, String imageUrl, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.cardId = Objects.requireNonNull(cardId);
        this.language = requireText(language, "language").toUpperCase();
        this.version = requireText(version, "version");
        this.rarity = trimToNull(rarity);
        this.imageUrl = trimToNull(imageUrl);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static CardVariant create(UUID cardId, String language, String version, String rarity, String imageUrl, Instant now) {
        return new CardVariant(UUID.randomUUID(), cardId, language, version, rarity, imageUrl, now);
    }

    public UUID getId() { return id; }
    public UUID getCardId() { return cardId; }
    public String getLanguage() { return language; }
    public String getVersion() { return version; }
    public String getRarity() { return rarity; }
    public String getImageUrl() { return imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
