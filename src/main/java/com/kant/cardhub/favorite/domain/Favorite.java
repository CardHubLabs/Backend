package com.kant.cardhub.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "listing_id", nullable = false, updatable = false)
    private UUID listingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    public Favorite(UUID id, UUID userId, UUID listingId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.listingId = listingId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

