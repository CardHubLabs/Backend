package com.kant.cardhub.listing.domain;

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
@Table(name = "listings")
public class Listing {

    public static final long MIN_PRICE = 500;
    public static final long MAX_PRICE = 100_000_000;

    @Id
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "card_variant_id", nullable = false)
    private UUID cardVariantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_condition", nullable = false, length = 40)
    private CardCondition cardCondition;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private long price;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ListingModerationStatus moderationStatus;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_by")
    private UUID blockedBy;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "reservation_order_id")
    private UUID reservationOrderId;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Listing() {
    }

    private Listing(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            CardCondition cardCondition,
            String description,
            long price,
            long shippingFee,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.sellerId = Objects.requireNonNull(sellerId);
        this.cardVariantId = Objects.requireNonNull(cardVariantId);
        this.cardCondition = Objects.requireNonNull(cardCondition);
        this.description = normalizeDescription(description);
        validateMoney(price, shippingFee);
        this.price = price;
        this.shippingFee = shippingFee;
        this.status = ListingStatus.ACTIVE;
        this.moderationStatus = ListingModerationStatus.CLEAR;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static Listing create(
            UUID sellerId,
            UUID cardVariantId,
            CardCondition condition,
            String description,
            long price,
            long shippingFee,
            Instant now
    ) {
        return new Listing(
                UUID.randomUUID(), sellerId, cardVariantId, condition, description, price, shippingFee, now
        );
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public UUID getCardVariantId() { return cardVariantId; }
    public CardCondition getCardCondition() { return cardCondition; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
    public long getShippingFee() { return shippingFee; }
    public ListingStatus getStatus() { return status; }
    public ListingModerationStatus getModerationStatus() { return moderationStatus; }
    public ListingEffectiveStatus getEffectiveStatus() {
        if (moderationStatus == ListingModerationStatus.BLOCKED) {
            return ListingEffectiveStatus.BLOCKED;
        }
        return switch (status) {
            case ACTIVE -> ListingEffectiveStatus.ACTIVE;
            case HIDDEN -> ListingEffectiveStatus.HIDDEN;
            case RESERVED -> ListingEffectiveStatus.RESERVED;
            case SOLD -> ListingEffectiveStatus.SOLD;
            case DELETED -> ListingEffectiveStatus.DELETED;
        };
    }
    public Instant getBlockedAt() { return blockedAt; }
    public UUID getBlockedBy() { return blockedBy; }
    public String getBlockedReason() { return blockedReason; }
    public UUID getReservationOrderId() { return reservationOrderId; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOwnedBy(UUID userId) {
        return sellerId.equals(userId);
    }

    public boolean isReservable() {
        return status == ListingStatus.ACTIVE && moderationStatus == ListingModerationStatus.CLEAR;
    }

    public boolean isBlocked() {
        return moderationStatus == ListingModerationStatus.BLOCKED;
    }

    public void update(
            UUID requesterId,
            CardCondition condition,
            String description,
            long price,
            long shippingFee,
            Instant now
    ) {
        requireOwner(requesterId);
        requireNotBlocked();
        if (status != ListingStatus.ACTIVE && status != ListingStatus.HIDDEN) throw invalidState();
        validateMoney(price, shippingFee);
        this.cardCondition = Objects.requireNonNull(condition);
        this.description = normalizeDescription(description);
        this.price = price;
        this.shippingFee = shippingFee;
        this.updatedAt = now;
    }

    public void hide(UUID requesterId, Instant now) {
        requireOwner(requesterId);
        requireNotBlocked();
        if (status != ListingStatus.ACTIVE) throw invalidState();
        this.status = ListingStatus.HIDDEN;
        this.updatedAt = now;
    }

    public void activate(UUID requesterId, Instant now) {
        requireOwner(requesterId);
        requireNotBlocked();
        if (status != ListingStatus.HIDDEN) throw invalidState();
        this.status = ListingStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void delete(UUID requesterId, Instant now) {
        requireOwner(requesterId);
        requireNotBlocked();
        if (status != ListingStatus.ACTIVE && status != ListingStatus.HIDDEN) throw invalidState();
        this.status = ListingStatus.DELETED;
        this.updatedAt = now;
    }

    public void block(UUID adminId, String reason, Instant now) {
        if (isBlocked() || status == ListingStatus.DELETED) throw invalidState();
        UUID normalizedAdminId = Objects.requireNonNull(adminId);
        Instant blockedAt = Objects.requireNonNull(now);
        String normalizedReason = normalizeBlockReason(reason);
        this.moderationStatus = ListingModerationStatus.BLOCKED;
        this.blockedAt = blockedAt;
        this.blockedBy = normalizedAdminId;
        this.blockedReason = normalizedReason;
        if (status == ListingStatus.ACTIVE) {
            this.status = ListingStatus.HIDDEN;
        }
        this.updatedAt = now;
    }

    public void unblock(Instant now) {
        if (!isBlocked()) throw invalidState();
        Instant unblockedAt = Objects.requireNonNull(now);
        this.moderationStatus = ListingModerationStatus.CLEAR;
        this.blockedAt = null;
        this.blockedBy = null;
        this.blockedReason = null;
        this.updatedAt = unblockedAt;
    }

    public void reserve(UUID orderId, Instant expiresAt, Instant now) {
        if (!isReservable()) throw new BusinessException(ErrorCode.LISTING_ALREADY_RESERVED);
        this.status = ListingStatus.RESERVED;
        this.reservationOrderId = Objects.requireNonNull(orderId);
        this.reservationExpiresAt = Objects.requireNonNull(expiresAt);
        this.updatedAt = Objects.requireNonNull(now);
    }

    public void releaseReservation(UUID orderId, Instant now) {
        if (status != ListingStatus.RESERVED || !Objects.equals(reservationOrderId, orderId)) throw invalidState();
        this.status = isBlocked() ? ListingStatus.HIDDEN : ListingStatus.ACTIVE;
        this.reservationOrderId = null;
        this.reservationExpiresAt = null;
        this.updatedAt = now;
    }

    public void markSold(UUID orderId, Instant now) {
        if (status != ListingStatus.RESERVED || !Objects.equals(reservationOrderId, orderId)) throw invalidState();
        this.status = ListingStatus.SOLD;
        this.reservationOrderId = null;
        this.reservationExpiresAt = null;
        this.updatedAt = now;
    }

    private void requireOwner(UUID requesterId) {
        if (!sellerId.equals(requesterId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void requireNotBlocked() {
        if (isBlocked()) throw invalidState();
    }

    private static void validateMoney(long price, long shippingFee) {
        if (price < MIN_PRICE || price > MAX_PRICE || shippingFee < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "가격 또는 배송비가 허용 범위를 벗어났습니다.");
        }
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 2_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "매물 설명은 2,000자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String normalizeBlockReason(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "차단 사유가 필요합니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "차단 사유는 500자 이하여야 합니다.");
        }
        return normalized;
    }

    private static BusinessException invalidState() {
        return new BusinessException(ErrorCode.LISTING_INVALID_STATE);
    }
}
