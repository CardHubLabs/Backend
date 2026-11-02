package com.kant.cardhub.shipment.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SweetTrackerPropertiesTest {

    @Test
    void appliesSafeTimeoutDefaultsToRequestFactory() {
        SweetTrackerProperties properties = new SweetTrackerProperties(
                "sweettracker", "https://example.com", "key", null, null);

        var requestFactory = SweetTrackerShipmentAdapter.requestFactory(properties);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(3_000);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(7_000);
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThatThrownBy(() -> new SweetTrackerProperties(
                "sweettracker", "https://example.com", "key", Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SweetTrackerProperties(
                "sweettracker", "https://example.com", "key", Duration.ofSeconds(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
