package com.kant.cardhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "card_sets")
public class CardSet {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "released_at")
    private LocalDate releasedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardSet() {
    }

    private CardSet(UUID id, UUID gameId, String name, String code, LocalDate releasedAt, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.gameId = Objects.requireNonNull(gameId);
        this.name = requireText(name, "name");
        this.code = requireText(code, "code");
        this.releasedAt = releasedAt;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static CardSet create(UUID gameId, String name, String code, LocalDate releasedAt, Instant now) {
        return new CardSet(UUID.randomUUID(), gameId, name, code, releasedAt, now);
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public LocalDate getReleasedAt() { return releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
