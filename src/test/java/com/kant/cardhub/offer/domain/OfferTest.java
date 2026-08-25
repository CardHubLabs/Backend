package com.kant.cardhub.offer.domain;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private final UUID listingId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();

    @Test
    void sellerCanCounterOnceAndBuyerCanAcceptCounter() {
        Offer offer = offer();

        offer.counter(sellerId, 9_000, 10_000, NOW.plusSeconds(1));
        offer.accept(buyerId, NOW.plusSeconds(2));

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(offer.getCounterCount()).isEqualTo(1);
        assertThat(offer.acceptedPrice()).isEqualTo(9_000);
    }

    @Test
    void buyerCannotAnswerInitialOfferOnBehalfOfSeller() {
        Offer offer = offer();

        assertThatThrownBy(() -> offer.accept(buyerId, NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.OFFER_FORBIDDEN);
    }

    @Test
    void acceptedOfferCanBeConsumedExactlyOnceByItsBuyerAndListing() {
        Offer offer = offer();
        offer.accept(sellerId, NOW.plusSeconds(1));

        assertThat(offer.consume(buyerId, listingId, NOW.plusSeconds(2))).isEqualTo(8_000);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.CONSUMED);
        assertThatThrownBy(() -> offer.consume(buyerId, listingId, NOW.plusSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.OFFER_INVALID_STATE);
    }

    @Test
    void openAndAcceptedOffersExpireAtBoundary() {
        Instant expiresAt = NOW.plus(48, ChronoUnit.HOURS);
        Offer pending = Offer.create(listingId, buyerId, sellerId, 8_000, expiresAt, NOW);
        Offer accepted = Offer.create(UUID.randomUUID(), buyerId, sellerId, 8_000, expiresAt, NOW);
        accepted.accept(sellerId, NOW.plusSeconds(1));

        assertThat(pending.expire(expiresAt.minusNanos(1))).isFalse();
        assertThat(pending.expire(expiresAt)).isTrue();
        assertThat(accepted.expire(expiresAt)).isTrue();
        assertThat(pending.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(accepted.getStatus()).isEqualTo(OfferStatus.EXPIRED);
    }

    private Offer offer() {
        return Offer.create(
                listingId, buyerId, sellerId, 8_000, NOW.plus(48, ChronoUnit.HOURS), NOW
        );
    }
}
