package com.kant.cardhub.listing.application;

import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.ListingEffectiveStatus;
import com.kant.cardhub.listing.domain.ListingModerationStatus;
import com.kant.cardhub.listing.domain.ListingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ListingView {
    private ListingView() {
    }

    public record Image(UUID id, String url, int sortOrder) {}

    @Schema(name = "ListingSummary", description = "목록용 매물 응답")
    public record Summary(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            CardCondition cardCondition,
            long price,
            long shippingFee,
            ListingStatus status,
            ListingModerationStatus moderationStatus,
            @Schema(
                    description = "moderation BLOCKED가 lifecycle status보다 우선하며, 그 외에는 status와 동일한 유효 상태",
                    allowableValues = {"ACTIVE", "HIDDEN", "RESERVED", "SOLD", "DELETED", "BLOCKED"},
                    example = "ACTIVE"
            )
            ListingEffectiveStatus effectiveStatus,
            Instant blockedAt,
            String blockedReason,
            String primaryImageUrl,
            Instant createdAt
    ) {}

    @Schema(name = "ListingDetail", description = "상세용 매물 응답")
    public record Detail(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            CardCondition cardCondition,
            String description,
            long price,
            long shippingFee,
            ListingStatus status,
            ListingModerationStatus moderationStatus,
            @Schema(
                    description = "moderation BLOCKED가 lifecycle status보다 우선하며, 그 외에는 status와 동일한 유효 상태",
                    allowableValues = {"ACTIVE", "HIDDEN", "RESERVED", "SOLD", "DELETED", "BLOCKED"},
                    example = "BLOCKED"
            )
            ListingEffectiveStatus effectiveStatus,
            Instant blockedAt,
            String blockedReason,
            List<Image> images,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
