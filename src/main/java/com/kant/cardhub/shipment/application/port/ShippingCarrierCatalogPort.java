package com.kant.cardhub.shipment.application.port;

import java.util.List;

public interface ShippingCarrierCatalogPort {

    String provider();

    List<Carrier> fetchCarriers();

    record Carrier(String code, String displayName) {
    }
}
