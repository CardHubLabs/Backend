package com.kant.cardhub.order.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderDeliveryProjectionPort {

    Optional<DeliveryProjection> findByOrderId(UUID orderId);

    record DeliveryProjection(
            String shipmentStatus,
            Instant deliveredAt,
            boolean autoConfirmHeld
    ) {
    }
}
