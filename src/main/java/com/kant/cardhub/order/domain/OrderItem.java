package com.kant.cardhub.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "card_variant_id", nullable = false)
    private UUID cardVariantId;

    @Column(name = "listing_title", nullable = false, length = 200)
    private String listingTitle;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderItem() {
    }

    OrderItem(Order order, OrderItemDraft draft, Instant now) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.listingId = draft.listingId();
        this.cardVariantId = draft.cardVariantId();
        this.listingTitle = draft.listingTitle();
        this.unitPrice = draft.price();
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public UUID getCardVariantId() {
        return cardVariantId;
    }

    public String getListingTitle() {
        return listingTitle;
    }

    public long getUnitPrice() {
        return unitPrice;
    }
}
