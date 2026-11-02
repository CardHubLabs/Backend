package com.kant.cardhub.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shipping_carriers")
public class ShippingCarrier {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShippingCarrier() {
    }

    public static ShippingCarrier create(
            String provider,
            String code,
            String displayName,
            int sortOrder,
            Instant now
    ) {
        ShippingCarrier carrier = new ShippingCarrier();
        carrier.id = UUID.randomUUID();
        carrier.provider = normalizeProvider(provider);
        carrier.code = requireText(code, "code", 40);
        carrier.displayName = requireText(displayName, "displayName", 100);
        carrier.active = true;
        carrier.sortOrder = sortOrder;
        carrier.createdAt = Objects.requireNonNull(now, "now");
        carrier.updatedAt = now;
        return carrier;
    }

    private static String normalizeProvider(String value) {
        return requireText(value, "provider", 32).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void synchronize(String displayName, int sortOrder, Instant now) {
        this.displayName = requireText(displayName, "displayName", 100);
        this.sortOrder = sortOrder;
        this.active = true;
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = Objects.requireNonNull(now);
    }
}
