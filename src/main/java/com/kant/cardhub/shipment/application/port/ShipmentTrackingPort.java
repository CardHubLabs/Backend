package com.kant.cardhub.shipment.application.port;

import com.kant.cardhub.shipment.domain.ShipmentStatus;

import java.time.Instant;

public interface ShipmentTrackingPort {

    TrackingResult track(String carrierCode, String trackingNumber);

    record TrackingResult(
            String carrierCode,
            String trackingNumber,
            ShipmentStatus status,
            String providerEventKey,
            String description,
            Instant eventAt
    ) {
    }
}
