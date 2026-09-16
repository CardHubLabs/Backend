package com.kant.cardhub.shipment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.shipment.application.ShippingCarrierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping/carriers")
@Tag(name = "Shipping", description = "배송정보와 활성 택배사 조회 API")
public class ShippingCarrierController {

    private final ShippingCarrierService carrierService;

    public ShippingCarrierController(ShippingCarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping
    @Operation(
            summary = "활성 택배사 목록 조회",
            description = "마지막으로 동기화된 활성 택배사 코드와 표시명을 반환합니다. "
                    + "운송장 등록·수정에는 이 목록의 code만 사용할 수 있습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "활성 택배사 목록 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "Bearer 인증 필요")
            }
    )
    public ApiResponse<List<ShippingCarrierService.CarrierView>> activeCarriers() {
        return ApiResponse.success(carrierService.activeCarriers());
    }
}
