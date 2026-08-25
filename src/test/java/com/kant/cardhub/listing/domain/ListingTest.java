package com.kant.cardhub.listing.domain;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private final UUID sellerId = UUID.randomUUID();

    @Test
    void activeAndHiddenListingsCanBeEdited() {
        Listing listing = listing();

        listing.update(sellerId, CardCondition.MINT, "  새 설명  ", 20_000, 4_000, NOW.plusSeconds(1));
        listing.hide(sellerId, NOW.plusSeconds(2));
        listing.update(sellerId, CardCondition.LIGHT_PLAYED, null, 19_000, 0, NOW.plusSeconds(3));

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(listing.getCardCondition()).isEqualTo(CardCondition.LIGHT_PLAYED);
        assertThat(listing.getDescription()).isNull();
        assertThat(listing.getPrice()).isEqualTo(19_000);
    }

    @Test
    void onlyReservationOwnerCanReleaseOrSellListing() {
        Listing listing = listing();
        UUID orderId = UUID.randomUUID();
        listing.reserve(orderId, NOW.plus(10, ChronoUnit.MINUTES), NOW);

        assertThatThrownBy(() -> listing.releaseReservation(UUID.randomUUID(), NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.LISTING_INVALID_STATE);

        listing.markSold(orderId, NOW.plusSeconds(2));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(listing.getReservationOrderId()).isNull();
        assertThat(listing.getReservationExpiresAt()).isNull();
    }

    @Test
    void nonOwnerCannotMutateListing() {
        Listing listing = listing();

        assertThatThrownBy(() -> listing.hide(UUID.randomUUID(), NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void reservedListingCannotBeEditedOrDeleted() {
        Listing listing = listing();
        listing.reserve(UUID.randomUUID(), NOW.plus(10, ChronoUnit.MINUTES), NOW);

        assertThatThrownBy(() -> listing.update(
                sellerId, CardCondition.MINT, null, 1_000, 0, NOW.plusSeconds(1)
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> listing.delete(sellerId, NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);
    }

    private Listing listing() {
        return Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT, "설명", 10_000, 3_000, NOW
        );
    }
}
