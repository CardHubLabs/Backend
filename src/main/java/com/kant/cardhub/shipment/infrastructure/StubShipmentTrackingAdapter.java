package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.application.port.ShippingCarrierCatalogPort;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Component
@Profile("!prod")
@ConditionalOnProperty(name = "cardhub.shipment.provider", havingValue = "stub", matchIfMissing = true)
public class StubShipmentTrackingAdapter implements ShipmentTrackingPort, ShippingCarrierCatalogPort {

    private final Clock clock;

    public StubShipmentTrackingAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public TrackingResult track(String carrierCode, String trackingNumber) {
        ShipmentStatus status = trackingNumber.startsWith("delivered_")
                ? ShipmentStatus.DELIVERED
                : ShipmentStatus.IN_TRANSIT;
        return new TrackingResult(
                carrierCode,
                trackingNumber,
                status,
                carrierCode + ":" + trackingNumber + ":" + status,
                "Stub tracking result",
                clock.instant()
        );
    }

    @Override
    public String provider() {
        return "stub";
    }

    @Override
    public java.util.List<Carrier> fetchCarriers() {
        return java.util.List.of(
                new Carrier("kr.cjlogistics", "CJ대한통운 (로컬 샘플)"),
                new Carrier("kr.hanjin", "한진택배 (로컬 샘플)"),
                new Carrier("kr.lotte", "롯데택배 (로컬 샘플)"),
                new Carrier("kr.epost", "우체국택배 (로컬 샘플)")
        );
    }
}
