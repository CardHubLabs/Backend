package com.kant.cardhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    private UUID id;

    @Column(name = "card_set_id", nullable = false)
    private UUID cardSetId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "name_ko", length = 200)
    private String nameKo;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "name_ja", length = 200)
    private String nameJa;

    @Column(length = 100)
    private String rarity;

    @Column(name = "card_number", nullable = false, length = 50)
    private String cardNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {
    }

    private Card(
            UUID id,
            UUID cardSetId,
            String name,
            String cardNumber,
            String nameKo,
            String nameEn,
            String nameJa,
            String rarity,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.cardSetId = Objects.requireNonNull(cardSetId);
        this.name = requireText(name, "name");
        this.cardNumber = requireText(cardNumber, "cardNumber");
        this.nameKo = trimToNull(nameKo);
        this.nameEn = trimToNull(nameEn);
        this.nameJa = trimToNull(nameJa);
        this.rarity = trimToNull(rarity);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static Card create(UUID cardSetId, String name, String cardNumber, Instant now) {
        return create(cardSetId, name, cardNumber, null, null, null, null, now);
    }

    public static Card create(
            UUID cardSetId,
            String name,
            String cardNumber,
            String nameKo,
            String nameEn,
            String nameJa,
            String rarity,
            Instant now
    ) {
        return new Card(
                UUID.randomUUID(), cardSetId, name, cardNumber,
                nameKo, nameEn, nameJa, rarity, now
        );
    }

    public UUID getId() { return id; }
    public UUID getCardSetId() { return cardSetId; }
    public String getName() { return name; }
    public String getNameKo() { return nameKo; }
    public String getNameEn() { return nameEn; }
    public String getNameJa() { return nameJa; }
    public String getRarity() { return rarity; }
    public String getCardNumber() { return cardNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(
            UUID cardSetId,
            String name,
            String cardNumber,
            String nameKo,
            String nameEn,
            String nameJa,
            String rarity,
            Instant now
    ) {
        this.cardSetId = Objects.requireNonNull(cardSetId);
        this.name = requireText(name, "name");
        this.cardNumber = requireText(cardNumber, "cardNumber");
        this.nameKo = trimToNull(nameKo);
        this.nameEn = trimToNull(nameEn);
        this.nameJa = trimToNull(nameJa);
        this.rarity = trimToNull(rarity);
        this.updatedAt = Objects.requireNonNull(now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
