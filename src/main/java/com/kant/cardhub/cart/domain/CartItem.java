package com.kant.cardhub.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CartItem() {
    }

    private CartItem(UUID userId, UUID listingId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.listingId = Objects.requireNonNull(listingId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static CartItem create(UUID userId, UUID listingId, Instant createdAt) {
        return new CartItem(userId, listingId, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getListingId() { return listingId; }
    public Instant getCreatedAt() { return createdAt; }
}
