package com.kant.cardhub.shipment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private final UUID buyerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();

    @Test
    void deliveredTimeDrivesNoticeAndAutoConfirmationBoundaries() {
        Shipment shipment = shipment();
        Instant deliveredAt = NOW.plusSeconds(30);
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, deliveredAt, NOW.plusSeconds(31));

        assertThat(shipment.noticeDue(deliveredAt.plus(Shipment.AUTO_CONFIRM_NOTICE_DELAY).minusNanos(1))).isFalse();
        assertThat(shipment.noticeDue(deliveredAt.plus(Shipment.AUTO_CONFIRM_NOTICE_DELAY))).isTrue();
        assertThat(shipment.autoConfirmDue(deliveredAt.plus(Shipment.AUTO_CONFIRM_DELAY).minusNanos(1))).isFalse();
        assertThat(shipment.autoConfirmDue(deliveredAt.plus(Shipment.AUTO_CONFIRM_DELAY))).isTrue();
    }

    @Test
    void holdBlocksAutomaticConfirmationUntilReleased() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, NOW, NOW);
        shipment.hold(buyerId, buyerId, NOW.plusSeconds(1));

        assertThat(shipment.autoConfirmDue(NOW.plus(Shipment.AUTO_CONFIRM_DELAY))).isFalse();

        shipment.releaseHold(NOW.plusSeconds(2));
        assertThat(shipment.autoConfirmDue(NOW.plus(Shipment.AUTO_CONFIRM_DELAY))).isTrue();
    }

    @Test
    void trackingCannotRegressAfterDelivered() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, NOW, NOW);

        assertThat(shipment.applyTrackingStatus(
                ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(1), NOW.plusSeconds(1))).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void olderProviderEventCannotOverwriteNewerStatus() {
        Shipment shipment = shipment();
        Instant newer = NOW.plusSeconds(20);
        shipment.applyTrackingStatus(ShipmentStatus.IN_TRANSIT, newer, newer);

        boolean changed = shipment.applyTrackingStatus(
                ShipmentStatus.EXCEPTION, NOW.plusSeconds(10), NOW.plusSeconds(30));

        assertThat(changed).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.getLastProviderEventAt()).isEqualTo(newer);
    }

    @Test
    void newerProviderEventCanRecoverBetweenExceptionAndInTransit() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(10), NOW.plusSeconds(10));
        shipment.applyTrackingStatus(ShipmentStatus.EXCEPTION, NOW.plusSeconds(20), NOW.plusSeconds(20));

        boolean recovered = shipment.applyTrackingStatus(
                ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(30), NOW.plusSeconds(30));

        assertThat(recovered).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.getLastProviderEventAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void deliveredIsTerminalEvenForNewerProviderEvent() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, NOW.plusSeconds(10), NOW.plusSeconds(10));

        boolean changed = shipment.applyTrackingStatus(
                ShipmentStatus.EXCEPTION, NOW.plusSeconds(20), NOW.plusSeconds(20));

        assertThat(changed).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.getDeliveredAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(shipment.getLastProviderEventAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void changingTrackingNumberStartsANewProviderTimeline() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(20), NOW.plusSeconds(20));

        shipment.updateTracking(sellerId, "kr.lotte", "new-number", NOW.plusSeconds(30));

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.getLastProviderEventAt()).isNull();
        assertThat(shipment.applyTrackingStatus(
                ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(5), NOW.plusSeconds(31))).isTrue();
    }

    @Test
    void deliveryBeforeShipmentCreationIsIgnored() {
        Shipment shipment = shipment();

        boolean changed = shipment.applyTrackingStatus(
                ShipmentStatus.DELIVERED, NOW.minusSeconds(1), NOW.plusSeconds(10));

        assertThat(changed).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.getDeliveredAt()).isNull();
    }

    @Test
    void providerEventFromTheFutureIsIgnored() {
        Shipment shipment = shipment();

        boolean changed = shipment.applyTrackingStatus(
                ShipmentStatus.DELIVERED, NOW.plusSeconds(11), NOW.plusSeconds(10));

        assertThat(changed).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.getDeliveredAt()).isNull();
    }

    @Test
    void onlyBuyerCanHold() {
        Shipment shipment = shipment();
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, NOW, NOW);

        assertThatThrownBy(() -> shipment.hold(UUID.randomUUID(), buyerId, NOW.plusSeconds(1)))
                .isInstanceOf(SecurityException.class);
    }

    private Shipment shipment() {
        return Shipment.register(UUID.randomUUID(), sellerId, "kr.cjlogistics", "123456", NOW);
    }
}
