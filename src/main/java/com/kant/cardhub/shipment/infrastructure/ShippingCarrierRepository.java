package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.shipment.domain.ShippingCarrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShippingCarrierRepository extends JpaRepository<ShippingCarrier, UUID> {

    List<ShippingCarrier> findAllByProviderAndActiveTrueOrderBySortOrderAscDisplayNameAsc(String provider);

    Optional<ShippingCarrier> findByProviderAndCodeAndActiveTrue(String provider, String code);

    List<ShippingCarrier> findAllByProvider(String provider);
}
