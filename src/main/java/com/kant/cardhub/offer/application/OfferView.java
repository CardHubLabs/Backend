package com.kant.cardhub.offer.application;

import com.kant.cardhub.offer.domain.OfferActionType;
import com.kant.cardhub.offer.domain.OfferStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OfferView {
    private OfferView() {
    }

    public record Detail(
            UUID id,
            UUID listingId,
            UUID buyerId,
            UUID sellerId,
            long offeredPrice,
            Long counterPrice,
            Long acceptedPrice,
            OfferStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Action(UUID actorId, OfferActionType type, Long price, Instant createdAt) {}

    public record WithHistory(Detail offer, List<Action> history) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
