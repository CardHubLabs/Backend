package com.kant.cardhub.listing.domain;

import com.kant.cardhub.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
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

    @Test
    void clearListingsExposeTheirLifecycleAsTheEffectiveStatus() {
        UUID sellerId = UUID.randomUUID();
        Listing active = listing(sellerId);
        Listing hidden = listing(sellerId);
        hidden.hide(sellerId, NOW.plusSeconds(1));
        Listing reserved = listing(sellerId);
        reserved.reserve(UUID.randomUUID(), NOW.plusSeconds(600), NOW.plusSeconds(1));
        Listing sold = listing(sellerId);
        UUID soldOrderId = UUID.randomUUID();
        sold.reserve(soldOrderId, NOW.plusSeconds(600), NOW.plusSeconds(1));
        sold.markSold(soldOrderId, NOW.plusSeconds(2));
        Listing deleted = listing(sellerId);
        deleted.delete(sellerId, NOW.plusSeconds(1));

        assertThat(List.of(active, hidden, reserved, sold, deleted))
                .extracting(Listing::getEffectiveStatus)
                .containsExactly(
                        ListingEffectiveStatus.ACTIVE,
                        ListingEffectiveStatus.HIDDEN,
                        ListingEffectiveStatus.RESERVED,
                        ListingEffectiveStatus.SOLD,
                        ListingEffectiveStatus.DELETED
                );
    }

    @Test
    void blockedModerationOverridesEveryPersistedLifecycleStatus() {
        UUID sellerId = UUID.randomUUID();
        Listing hidden = listing(sellerId);
        hidden.block(UUID.randomUUID(), "관리자 차단", NOW.plusSeconds(1));

        Listing reserved = listing(sellerId);
        reserved.reserve(UUID.randomUUID(), NOW.plusSeconds(600), NOW.plusSeconds(1));
        reserved.block(UUID.randomUUID(), "관리자 차단", NOW.plusSeconds(2));

        Listing sold = listing(sellerId);
        UUID orderId = UUID.randomUUID();
        sold.reserve(orderId, NOW.plusSeconds(600), NOW.plusSeconds(1));
        sold.markSold(orderId, NOW.plusSeconds(2));
        sold.block(UUID.randomUUID(), "관리자 차단", NOW.plusSeconds(3));

        Listing deleted = listing(sellerId);
        deleted.delete(sellerId, NOW.plusSeconds(1));
        // JPA can hydrate this historical combination even though domain commands do not create it.
        ReflectionTestUtils.setField(deleted, "moderationStatus", ListingModerationStatus.BLOCKED);

        assertThat(List.of(hidden, reserved, sold, deleted))
                .extracting(Listing::getEffectiveStatus)
                .containsOnly(ListingEffectiveStatus.BLOCKED);
    }

    @Test
    void unblockingRestoresTheLifecycleAsTheEffectiveStatus() {
        Listing listing = listing(UUID.randomUUID());
        listing.block(UUID.randomUUID(), "관리자 차단", NOW.plusSeconds(1));

        listing.unblock(NOW.plusSeconds(2));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(listing.getModerationStatus()).isEqualTo(ListingModerationStatus.CLEAR);
        assertThat(listing.getEffectiveStatus()).isEqualTo(ListingEffectiveStatus.HIDDEN);
    }

    private static Listing listing(UUID sellerId) {
        return Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW
        );
    }
}
