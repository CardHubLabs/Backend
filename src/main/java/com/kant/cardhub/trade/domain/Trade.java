package com.kant.cardhub.trade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "card_variant_id", nullable = false)
    private UUID cardVariantId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private long price;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private boolean excluded;

    @Column(name = "exclusion_reason", length = 100)
    private String exclusionReason;

    @Column(name = "excluded_at")
    private Instant excludedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Trade() {
    }

    public static Trade create(
            UUID orderId,
            UUID orderItemId,
            UUID listingId,
            UUID cardVariantId,
            UUID buyerId,
            UUID sellerId,
            long price,
            Instant occurredAt
    ) {
        Trade trade = new Trade();
        trade.id = UUID.randomUUID();
        trade.orderId = orderId;
        trade.orderItemId = orderItemId;
        trade.listingId = listingId;
        trade.cardVariantId = cardVariantId;
        trade.buyerId = buyerId;
        trade.sellerId = sellerId;
        trade.price = price;
        trade.occurredAt = occurredAt;
        trade.createdAt = occurredAt;
        return trade;
    }

    public void exclude(String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Trade exclusion reason is required");
        }
        excluded = true;
        exclusionReason = reason.substring(0, Math.min(100, reason.length()));
        excludedAt = now;
    }

    public void restore() {
        excluded = false;
        exclusionReason = null;
        excludedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getListingId() { return listingId; }
    public UUID getCardVariantId() { return cardVariantId; }
    public long getPrice() { return price; }
    public Instant getOccurredAt() { return occurredAt; }
    public boolean isExcluded() { return excluded; }
}
