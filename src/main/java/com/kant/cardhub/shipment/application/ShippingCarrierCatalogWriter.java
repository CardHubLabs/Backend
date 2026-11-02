package com.kant.cardhub.shipment.application;

import com.kant.cardhub.shipment.application.port.ShippingCarrierCatalogPort;
import com.kant.cardhub.shipment.domain.ShippingCarrier;
import com.kant.cardhub.shipment.infrastructure.ShippingCarrierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShippingCarrierCatalogWriter {

    private final ShippingCarrierRepository repository;
    private final Clock clock;

    public ShippingCarrierCatalogWriter(ShippingCarrierRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public int replace(String provider, List<ShippingCarrierCatalogPort.Carrier> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            throw new IllegalStateException("An empty carrier catalogue cannot replace the last successful data");
        }
        Map<String, ShippingCarrier> existing = new HashMap<>();
        repository.findAllByProvider(provider).forEach(carrier -> existing.put(carrier.getCode(), carrier));
        int sortOrder = 0;
        for (ShippingCarrierCatalogPort.Carrier item : incoming) {
            ShippingCarrier carrier = existing.remove(item.code());
            if (carrier == null) {
                repository.save(ShippingCarrier.create(
                        provider, item.code(), item.displayName(), sortOrder, clock.instant()));
            } else {
                carrier.synchronize(item.displayName(), sortOrder, clock.instant());
            }
            sortOrder += 10;
        }
        existing.values().forEach(carrier -> carrier.deactivate(clock.instant()));
        return incoming.size();
    }
}
