package com.kant.cardhub.offer.domain;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "offers")
public class Offer {

    public static final long MIN_PRICE = 500;

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "offered_price", nullable = false)
    private long offeredPrice;

    @Column(name = "counter_price")
    private Long counterPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferStatus status;

    @Column(name = "counter_count", nullable = false)
    private int counterCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Offer() {
    }

    private Offer(
            UUID listingId,
            UUID buyerId,
            UUID sellerId,
            long offeredPrice,
            Instant expiresAt,
            Instant now
    ) {
        if (offeredPrice < MIN_PRICE) throw invalidPrice();
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("expiresAt must be after now");
        this.id = UUID.randomUUID();
        this.listingId = Objects.requireNonNull(listingId);
        this.buyerId = Objects.requireNonNull(buyerId);
        this.sellerId = Objects.requireNonNull(sellerId);
        this.offeredPrice = offeredPrice;
        this.status = OfferStatus.PENDING;
        this.counterCount = 0;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Offer create(
            UUID listingId,
            UUID buyerId,
            UUID sellerId,
            long offeredPrice,
            Instant expiresAt,
            Instant now
    ) {
        return new Offer(listingId, buyerId, sellerId, offeredPrice, expiresAt, now);
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getSellerId() { return sellerId; }
    public long getOfferedPrice() { return offeredPrice; }
    public Long getCounterPrice() { return counterPrice; }
    public OfferStatus getStatus() { return status; }
    public int getCounterCount() { return counterCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOpen() {
        return status == OfferStatus.PENDING || status == OfferStatus.COUNTERED;
    }

    public boolean isExpirable() {
        return isOpen() || status == OfferStatus.ACCEPTED;
    }

    public void accept(UUID actorId, Instant now) {
        requireNotExpired(now);
        if (status == OfferStatus.PENDING) requireActor(sellerId, actorId);
        else if (status == OfferStatus.COUNTERED) requireActor(buyerId, actorId);
        else throw invalidState();
        this.status = OfferStatus.ACCEPTED;
        this.acceptedAt = now;
        this.updatedAt = now;
    }

    public void reject(UUID actorId, Instant now) {
        requireNotExpired(now);
        if (status == OfferStatus.PENDING) requireActor(sellerId, actorId);
        else if (status == OfferStatus.COUNTERED) requireActor(buyerId, actorId);
        else throw invalidState();
        this.status = OfferStatus.REJECTED;
        this.updatedAt = now;
    }

    public void counter(UUID actorId, long price, long listingPrice, Instant now) {
        requireNotExpired(now);
        if (status != OfferStatus.PENDING || counterCount != 0) throw invalidState();
        requireActor(sellerId, actorId);
        if (price < MIN_PRICE || price <= offeredPrice || price > listingPrice) throw invalidPrice();
        this.counterPrice = price;
        this.counterCount = 1;
        this.status = OfferStatus.COUNTERED;
        this.updatedAt = now;
    }

    public boolean expire(Instant now) {
        if (!isExpirable() || now.isBefore(expiresAt)) return false;
        this.status = OfferStatus.EXPIRED;
        this.updatedAt = now;
        return true;
    }

    public boolean expireForListingBlock(Instant now) {
        if (!isExpirable()) return false;
        this.status = OfferStatus.EXPIRED;
        this.updatedAt = Objects.requireNonNull(now);
        return true;
    }

    public void rejectAfterCompetingOfferAccepted(Instant now) {
        if (!isOpen()) return;
        this.status = OfferStatus.REJECTED;
        this.updatedAt = now;
    }

    public long acceptedPrice() {
        if (status != OfferStatus.ACCEPTED && status != OfferStatus.CONSUMED) throw invalidState();
        return counterPrice == null ? offeredPrice : counterPrice;
    }

    public long consume(UUID actorBuyerId, UUID expectedListingId, Instant now) {
        if (status != OfferStatus.ACCEPTED) throw invalidState();
        requireActor(buyerId, actorBuyerId);
        if (!listingId.equals(expectedListingId)) throw new BusinessException(ErrorCode.OFFER_FORBIDDEN);
        if (!now.isBefore(expiresAt)) {
            this.status = OfferStatus.EXPIRED;
            this.updatedAt = now;
            throw invalidState();
        }
        long price = acceptedPrice();
        this.status = OfferStatus.CONSUMED;
        this.consumedAt = now;
        this.updatedAt = now;
        return price;
    }

    private void requireNotExpired(Instant now) {
        if (!now.isBefore(expiresAt)) {
            expire(now);
            throw invalidState();
        }
    }

    private static void requireActor(UUID expected, UUID actual) {
        if (!expected.equals(actual)) throw new BusinessException(ErrorCode.OFFER_FORBIDDEN);
    }

    private static BusinessException invalidState() {
        return new BusinessException(ErrorCode.OFFER_INVALID_STATE);
    }

    private static BusinessException invalidPrice() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "제안 가격이 올바르지 않습니다.");
    }
}
