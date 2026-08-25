package com.kant.cardhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "card_games")
public class CardGame {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardGame() {
    }

    private CardGame(UUID id, String name, String slug, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.name = requireText(name, "name");
        this.slug = requireText(slug, "slug").toLowerCase(Locale.ROOT);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static CardGame create(String name, String slug, Instant now) {
        return new CardGame(UUID.randomUUID(), name, slug, now);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String slug, Instant now) {
        this.name = requireText(name, "name");
        this.slug = requireText(slug, "slug").toLowerCase(Locale.ROOT);
        this.updatedAt = Objects.requireNonNull(now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
