package com.kant.cardhub.shipment.application;

import com.kant.cardhub.shipment.application.port.ShippingCarrierCatalogPort;
import org.springframework.stereotype.Service;

@Service
public class ShippingCarrierCatalogSyncService {

    private final ShippingCarrierCatalogPort catalogPort;
    private final ShippingCarrierCatalogWriter writer;

    public ShippingCarrierCatalogSyncService(
            ShippingCarrierCatalogPort catalogPort,
            ShippingCarrierCatalogWriter writer
    ) {
        this.catalogPort = catalogPort;
        this.writer = writer;
    }

    /** Fetches outside the database transaction, then atomically replaces only on success. */
    public int synchronize() {
        return writer.replace(catalogPort.provider(), catalogPort.fetchCarriers());
    }
}
