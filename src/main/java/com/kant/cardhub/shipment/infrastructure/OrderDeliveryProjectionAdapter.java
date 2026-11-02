package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.order.application.port.OrderDeliveryProjectionPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OrderDeliveryProjectionAdapter implements OrderDeliveryProjectionPort {

    private final ShipmentRepository shipmentRepository;

    public OrderDeliveryProjectionAdapter(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Override
    public Optional<DeliveryProjection> findByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(shipment -> new DeliveryProjection(
                        shipment.getStatus().name(),
                        shipment.getDeliveredAt(),
                        shipment.isAutoConfirmHeld()));
    }
}
