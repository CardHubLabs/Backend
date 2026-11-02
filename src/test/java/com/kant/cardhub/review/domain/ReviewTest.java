package com.kant.cardhub.review.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void tagsAreDeduplicatedAndRoundTrip() {
        Review review = Review.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5,
                List.of("FAST_SHIPPING", "CAREFUL_PACKING", "FAST_SHIPPING"), "좋아요", NOW);

        assertThat(review.getTags()).containsExactly("FAST_SHIPPING", "CAREFUL_PACKING");
    }

    @Test
    void invalidRatingAndUnsafeTagAreRejected() {
        assertThatThrownBy(() -> Review.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0,
                List.of(), null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Review.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5,
                List.of("not allowed"), null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
