package com.kant.cardhub.shipment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.shipment.application.ShippingCarrierService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping/carriers")
public class ShippingCarrierController {

    private final ShippingCarrierService carrierService;

    public ShippingCarrierController(ShippingCarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping
    public ApiResponse<List<ShippingCarrierService.CarrierView>> activeCarriers() {
        return ApiResponse.success(carrierService.activeCarriers());
    }
}
