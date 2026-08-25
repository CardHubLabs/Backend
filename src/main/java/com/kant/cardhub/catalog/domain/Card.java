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

    @Column(name = "card_number", nullable = false, length = 50)
    private String cardNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {
    }

    private Card(UUID id, UUID cardSetId, String name, String cardNumber, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.cardSetId = Objects.requireNonNull(cardSetId);
        this.name = requireText(name, "name");
        this.cardNumber = requireText(cardNumber, "cardNumber");
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static Card create(UUID cardSetId, String name, String cardNumber, Instant now) {
        return new Card(UUID.randomUUID(), cardSetId, name, cardNumber, now);
    }

    public UUID getId() { return id; }
    public UUID getCardSetId() { return cardSetId; }
    public String getName() { return name; }
    public String getCardNumber() { return cardNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
