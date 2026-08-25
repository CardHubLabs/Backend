package com.kant.cardhub.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "listing_images")
public class ListingImage {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "upload_session_id", nullable = false, unique = true)
    private UUID uploadSessionId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ListingImage() {
    }

    private ListingImage(UUID listingId, UUID uploadSessionId, String storageKey, int sortOrder, Instant now) {
        this.id = UUID.randomUUID();
        this.listingId = Objects.requireNonNull(listingId);
        this.uploadSessionId = Objects.requireNonNull(uploadSessionId);
        this.storageKey = Objects.requireNonNull(storageKey);
        this.sortOrder = sortOrder;
        this.createdAt = Objects.requireNonNull(now);
    }

    public static ListingImage create(UUID listingId, UUID uploadSessionId, String storageKey, int order, Instant now) {
        return new ListingImage(listingId, uploadSessionId, storageKey, order, now);
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getUploadSessionId() { return uploadSessionId; }
    public String getStorageKey() { return storageKey; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
