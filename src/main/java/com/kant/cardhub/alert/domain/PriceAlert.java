package com.kant.cardhub.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    public static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "listing_id", nullable = false, updatable = false)
    private UUID listingId;

    @Column(name = "baseline_price", nullable = false, updatable = false)
    private long baselinePrice;

    @Column(name = "threshold_percentage", nullable = false, updatable = false)
    private int thresholdPercentage;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "triggered_price")
    private Long triggeredPrice;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PriceAlert() {
    }

    public PriceAlert(
            UUID id,
            UUID userId,
            UUID listingId,
            long baselinePrice,
            int thresholdPercentage,
            Instant now
    ) {
        if (baselinePrice < 0) {
            throw new IllegalArgumentException("baselinePrice must not be negative");
        }
        if (thresholdPercentage < 1 || thresholdPercentage > 99) {
            throw new IllegalArgumentException("thresholdPercentage must be between 1 and 99");
        }
        this.id = id;
        this.userId = userId;
        this.listingId = listingId;
        this.baselinePrice = baselinePrice;
        this.thresholdPercentage = thresholdPercentage;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean shouldTrigger(long currentPrice) {
        return active && currentPrice >= 0
                && currentPrice * 100L <= baselinePrice * (100L - thresholdPercentage);
    }

    public void trigger(long currentPrice, Instant now) {
        if (!shouldTrigger(currentPrice)) {
            throw new IllegalStateException("price alert is not eligible to trigger");
        }
        this.active = false;
        this.triggeredPrice = currentPrice;
        this.triggeredAt = now;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (!active) {
            return;
        }
        this.active = false;
        this.updatedAt = now;
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

    public long getBaselinePrice() {
        return baselinePrice;
    }

    public int getThresholdPercentage() {
        return thresholdPercentage;
    }

    public boolean isActive() {
        return active;
    }

    public Long getTriggeredPrice() {
        return triggeredPrice;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

