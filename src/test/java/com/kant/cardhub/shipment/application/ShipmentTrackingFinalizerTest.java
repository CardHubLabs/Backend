package com.kant.cardhub.shipment.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentEventRepository;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipmentTrackingFinalizerTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void staleDeliveredEventIsAuditedButDoesNotDeliverOrder() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipmentEventRepository eventRepository = mock(ShipmentEventRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = mock(Order.class);
        Shipment shipment = Shipment.register(
                UUID.randomUUID(), UUID.randomUUID(), "kr.cjlogistics", "123456", NOW);
        shipment.applyTrackingStatus(ShipmentStatus.IN_TRANSIT, NOW.plusSeconds(20), NOW.plusSeconds(20));
        when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipmentRepository.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        when(orderRepository.findByIdForUpdate(shipment.getOrderId())).thenReturn(Optional.of(order));

        ShipmentTrackingFinalizer finalizer = new ShipmentTrackingFinalizer(
                shipmentRepository, eventRepository, orderRepository, Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC));
        finalizer.apply(shipment.getId(), new ShipmentTrackingPort.TrackingResult(
                shipment.getCarrierCode(), shipment.getTrackingNumber(),
                ShipmentStatus.DELIVERED, null, "old delivery", NOW.plusSeconds(10)));

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.getLastProviderEventAt()).isEqualTo(NOW.plusSeconds(20));
        verify(order, never()).markDelivered(org.mockito.ArgumentMatchers.any());
        verify(eventRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resultForReplacedTrackingNumberCannotDeliverNewShipmentOrOrder() {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        ShipmentEventRepository eventRepository = mock(ShipmentEventRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = mock(Order.class);
        UUID sellerId = UUID.randomUUID();
        Shipment shipment = Shipment.register(
                UUID.randomUUID(), sellerId, "kr.cjlogistics", "old-number", NOW);
        String polledCarrier = shipment.getCarrierCode();
        String polledTracking = shipment.getTrackingNumber();
        shipment.updateTracking(sellerId, "kr.lotte", "new-number", NOW.plusSeconds(10));
        when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipmentRepository.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        when(orderRepository.findByIdForUpdate(shipment.getOrderId())).thenReturn(Optional.of(order));

        ShipmentTrackingFinalizer finalizer = new ShipmentTrackingFinalizer(
                shipmentRepository, eventRepository, orderRepository, Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC));
        finalizer.apply(shipment.getId(), new ShipmentTrackingPort.TrackingResult(
                polledCarrier, polledTracking, ShipmentStatus.DELIVERED,
                "old-delivery", "old tracking delivered", NOW.plusSeconds(20)));

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
        assertThat(shipment.getLastProviderEventAt()).isNull();
        verify(order, never()).markDelivered(org.mockito.ArgumentMatchers.any());
        verify(orderRepository, never()).findByIdForUpdate(org.mockito.ArgumentMatchers.any());
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(eventRepository, never()).insertIgnoringDuplicate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
