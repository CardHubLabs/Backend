package com.kant.cardhub.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    public static final Duration TRACKING_INTERVAL = Duration.ofMinutes(15);
    public static final Duration AUTO_CONFIRM_NOTICE_DELAY = Duration.ofDays(6);
    public static final Duration AUTO_CONFIRM_DELAY = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "carrier_code", nullable = false, length = 40)
    private String carrierCode;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShipmentStatus status;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "auto_confirm_held", nullable = false)
    private boolean autoConfirmHeld;

    @Column(name = "auto_confirm_notice_sent_at")
    private Instant autoConfirmNoticeSentAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_provider_event_at")
    private Instant lastProviderEventAt;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Shipment() {
    }

    public static Shipment register(
            UUID orderId,
            UUID sellerId,
            String carrierCode,
            String trackingNumber,
            Instant now
    ) {
        validateTracking(carrierCode, trackingNumber);
        Shipment shipment = new Shipment();
        shipment.id = UUID.randomUUID();
        shipment.orderId = Objects.requireNonNull(orderId);
        shipment.sellerId = Objects.requireNonNull(sellerId);
        shipment.carrierCode = carrierCode.trim();
        shipment.trackingNumber = trackingNumber.trim();
        shipment.status = ShipmentStatus.READY;
        shipment.nextCheckAt = now;
        shipment.createdAt = now;
        shipment.updatedAt = now;
        return shipment;
    }

    public void updateTracking(UUID actorId, String carrierCode, String trackingNumber, Instant now) {
        requireSeller(actorId);
        if (status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Delivered shipment cannot be edited");
        }
        validateTracking(carrierCode, trackingNumber);
        this.carrierCode = carrierCode.trim();
        this.trackingNumber = trackingNumber.trim();
        this.status = ShipmentStatus.READY;
        this.lastProviderEventAt = null;
        this.nextCheckAt = now;
        this.consecutiveFailures = 0;
        this.updatedAt = now;
    }

    public boolean applyTrackingStatus(ShipmentStatus incoming, Instant eventAt, Instant now) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(now, "now");
        if (status == ShipmentStatus.DELIVERED) {
            checked(now);
            return false;
        }

        Instant providerEventAt = eventAt == null ? now : eventAt;
        if (providerEventAt.isBefore(createdAt) || providerEventAt.isAfter(now)) {
            checked(now);
            return false;
        }
        if (lastProviderEventAt != null && !providerEventAt.isAfter(lastProviderEventAt)) {
            checked(now);
            return false;
        }

        boolean changed = false;
        if (incoming == ShipmentStatus.DELIVERED) {
            status = ShipmentStatus.DELIVERED;
            deliveredAt = providerEventAt;
            changed = true;
        } else if (incoming == ShipmentStatus.IN_TRANSIT && status != ShipmentStatus.IN_TRANSIT) {
            status = ShipmentStatus.IN_TRANSIT;
            changed = true;
        } else if (incoming == ShipmentStatus.EXCEPTION && status != ShipmentStatus.EXCEPTION) {
            status = ShipmentStatus.EXCEPTION;
            changed = true;
        }
        lastProviderEventAt = providerEventAt;
        checked(now);
        return changed;
    }

    public boolean matchesTracking(String expectedCarrierCode, String expectedTrackingNumber) {
        return carrierCode.equals(expectedCarrierCode) && trackingNumber.equals(expectedTrackingNumber);
    }

    public void trackingFailed(Instant now) {
        consecutiveFailures++;
        lastCheckedAt = now;
        long minutes = Math.min(360, 15L << Math.min(consecutiveFailures, 4));
        nextCheckAt = now.plus(Duration.ofMinutes(minutes));
        updatedAt = now;
    }

    private void checked(Instant now) {
        consecutiveFailures = 0;
        lastCheckedAt = now;
        nextCheckAt = status == ShipmentStatus.DELIVERED
                ? now.plus(AUTO_CONFIRM_DELAY)
                : now.plus(TRACKING_INTERVAL);
        updatedAt = now;
    }

    public void hold(UUID buyerId, UUID actualBuyerId, Instant now) {
        if (!actualBuyerId.equals(buyerId)) {
            throw new SecurityException("Only the buyer may hold auto confirmation");
        }
        if (status != ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Only a delivered order can be held");
        }
        autoConfirmHeld = true;
        updatedAt = now;
    }

    public void releaseHold(Instant now) {
        autoConfirmHeld = false;
        updatedAt = now;
    }

    public boolean noticeDue(Instant now) {
        return status == ShipmentStatus.DELIVERED
                && deliveredAt != null
                && autoConfirmNoticeSentAt == null
                && !now.isBefore(deliveredAt.plus(AUTO_CONFIRM_NOTICE_DELAY));
    }

    public void markNoticeSent(Instant now) {
        if (!noticeDue(now)) {
            throw new IllegalStateException("Auto confirmation notice is not due");
        }
        autoConfirmNoticeSentAt = now;
        updatedAt = now;
    }

    public boolean autoConfirmDue(Instant now) {
        return status == ShipmentStatus.DELIVERED
                && !autoConfirmHeld
                && deliveredAt != null
                && !now.isBefore(deliveredAt.plus(AUTO_CONFIRM_DELAY));
    }

    private void requireSeller(UUID actorId) {
        if (!sellerId.equals(actorId)) {
            throw new SecurityException("Only the seller may edit shipment tracking");
        }
    }

    private static void validateTracking(String carrierCode, String trackingNumber) {
        if (carrierCode == null || carrierCode.isBlank() || trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Carrier code and tracking number are required");
        }
        if (carrierCode.length() > 40 || trackingNumber.length() > 100) {
            throw new IllegalArgumentException("Tracking data is too long");
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getSellerId() { return sellerId; }
    public String getCarrierCode() { return carrierCode; }
    public String getTrackingNumber() { return trackingNumber; }
    public ShipmentStatus getStatus() { return status; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public boolean isAutoConfirmHeld() { return autoConfirmHeld; }
    public Instant getAutoConfirmNoticeSentAt() { return autoConfirmNoticeSentAt; }
    public Instant getLastProviderEventAt() { return lastProviderEventAt; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public Instant getCreatedAt() { return createdAt; }
}
