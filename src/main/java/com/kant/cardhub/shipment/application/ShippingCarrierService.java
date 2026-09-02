package com.kant.cardhub.shipment.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.shipment.domain.ShippingCarrier;
import com.kant.cardhub.shipment.infrastructure.ShippingCarrierRepository;
import com.kant.cardhub.shipment.infrastructure.SweetTrackerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ShippingCarrierService {

    private final ShippingCarrierRepository carrierRepository;
    private final String provider;

    public ShippingCarrierService(
            ShippingCarrierRepository carrierRepository,
            SweetTrackerProperties properties
    ) {
        this.carrierRepository = carrierRepository;
        this.provider = normalizeProvider(properties.provider());
    }

    @Transactional(readOnly = true)
    public List<CarrierView> activeCarriers() {
        return carrierRepository.findAllByProviderAndActiveTrueOrderBySortOrderAscDisplayNameAsc(provider)
                .stream()
                .map(CarrierView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String requireActiveCode(String requestedCode) {
        String code = requestedCode == null ? "" : requestedCode.trim();
        return carrierRepository.findByProviderAndCodeAndActiveTrue(provider, code)
                .map(ShippingCarrier::getCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "현재 배송 연동에서 지원하지 않는 택배사 코드입니다."
                ));
    }

    private static String normalizeProvider(String value) {
        return value == null || value.isBlank() ? "stub" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CarrierView(String code, String displayName) {
        static CarrierView from(ShippingCarrier carrier) {
            return new CarrierView(carrier.getCode(), carrier.getDisplayName());
        }
    }
}
