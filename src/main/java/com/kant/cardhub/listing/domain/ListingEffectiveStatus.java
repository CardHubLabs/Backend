package com.kant.cardhub.listing.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Client-facing listing status that combines the lifecycle and moderation axes.
 */
@Schema(
        name = "ListingEffectiveStatus",
        description = "클라이언트용 유효 상태. moderationStatus가 BLOCKED이면 lifecycle보다 우선해 BLOCKED이며, "
                + "그 외에는 status와 동일합니다."
)
public enum ListingEffectiveStatus {
    ACTIVE,
    HIDDEN,
    RESERVED,
    SOLD,
    DELETED,
    BLOCKED
}
