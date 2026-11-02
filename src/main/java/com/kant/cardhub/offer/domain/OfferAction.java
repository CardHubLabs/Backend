package com.kant.cardhub.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "offer_actions")
public class OfferAction {

    @Id
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private OfferActionType actionType;

    private Long price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OfferAction() {
    }

    private OfferAction(UUID offerId, UUID actorId, OfferActionType actionType, Long price, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.offerId = Objects.requireNonNull(offerId);
        this.actorId = actorId;
        this.actionType = Objects.requireNonNull(actionType);
        this.price = price;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static OfferAction create(
            UUID offerId, UUID actorId, OfferActionType actionType, Long price, Instant createdAt
    ) {
        return new OfferAction(offerId, actorId, actionType, price, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getActorId() { return actorId; }
    public OfferActionType getActionType() { return actionType; }
    public Long getPrice() { return price; }
    public Instant getCreatedAt() { return createdAt; }
}
