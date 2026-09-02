package com.kant.cardhub.listing.domain;

import com.kant.cardhub.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingModerationTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void blockedActiveListingBecomesHiddenAndOwnerCannotReactivate() {
        UUID sellerId = UUID.randomUUID();
        Listing listing = Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW);

        listing.block(UUID.randomUUID(), "위조 이미지", NOW.plusSeconds(1));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.BLOCKED);
        assertThatThrownBy(() -> listing.activate(sellerId, NOW.plusSeconds(2)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blockedReservationReleasesToHiddenWithoutBreakingTheOrderOwnership() {
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Listing listing = Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW);
        listing.reserve(orderId, NOW.plusSeconds(600), NOW.plusSeconds(1));
        listing.block(UUID.randomUUID(), "관리자 확인 필요", NOW.plusSeconds(2));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);

        listing.releaseReservation(orderId, NOW.plusSeconds(3));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.BLOCKED);
    }
}
