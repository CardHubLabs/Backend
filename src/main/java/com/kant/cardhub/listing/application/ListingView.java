package com.kant.cardhub.listing.application;

import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.ListingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ListingView {
    private ListingView() {
    }

    public record Image(UUID id, String url, int sortOrder) {}

    public record Summary(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            CardCondition cardCondition,
            long price,
            long shippingFee,
            ListingStatus status,
            String primaryImageUrl,
            Instant createdAt
    ) {}

    public record Detail(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            CardCondition cardCondition,
            String description,
            long price,
            long shippingFee,
            ListingStatus status,
            List<Image> images,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
