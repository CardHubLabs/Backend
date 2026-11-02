package com.kant.cardhub.alert.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceAlertTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void triggersAtConfiguredPercentageBoundaryOnlyOnce() {
        PriceAlert alert = new PriceAlert(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10_000, 10, NOW
        );

        assertThat(alert.shouldTrigger(9_001)).isFalse();
        assertThat(alert.shouldTrigger(9_000)).isTrue();

        alert.trigger(9_000, NOW.plusSeconds(1));

        assertThat(alert.isActive()).isFalse();
        assertThat(alert.getTriggeredPrice()).isEqualTo(9_000);
        assertThat(alert.getTriggeredAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(alert.shouldTrigger(8_000)).isFalse();
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new PriceAlert(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10_000, 0, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

