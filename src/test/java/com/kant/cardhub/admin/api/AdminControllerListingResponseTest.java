package com.kant.cardhub.admin.api;

import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingEffectiveStatus;
import com.kant.cardhub.listing.domain.ListingModerationStatus;
import com.kant.cardhub.listing.domain.ListingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerListingResponseTest {

    @Test
    void adminResponseKeepsRawStatusesAndAddsEffectiveStatus() {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        Listing listing = Listing.create(
                UUID.randomUUID(), UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, now
        );
        listing.block(UUID.randomUUID(), "관리자 차단", now.plusSeconds(1));

        AdminController.ListingResponse response = AdminController.ListingResponse.from(listing);

        assertThat(response.status()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(response.moderationStatus()).isEqualTo(ListingModerationStatus.BLOCKED);
        assertThat(response.effectiveStatus()).isEqualTo(ListingEffectiveStatus.BLOCKED);
    }
}
