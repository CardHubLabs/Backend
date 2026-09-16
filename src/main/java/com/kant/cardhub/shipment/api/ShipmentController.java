package com.kant.cardhub.shipment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.shipment.application.ShipmentApplicationService;
import com.kant.cardhub.shipment.application.ShipmentAutoConfirmationService;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentEvent;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Shipping", description = "배송정보와 활성 택배사 조회 API")
public class ShipmentController {

    private final ShipmentApplicationService shipmentService;
    private final ShipmentAutoConfirmationService autoConfirmationService;

    public ShipmentController(
            ShipmentApplicationService shipmentService,
            ShipmentAutoConfirmationService autoConfirmationService
    ) {
        this.shipmentService = shipmentService;
        this.autoConfirmationService = autoConfirmationService;
    }

    @PostMapping("/orders/{orderId}/shipment")
    @Operation(
            summary = "운송장 등록",
            description = "판매자가 PAID 주문에 활성 택배사 코드와 운송장 번호를 등록합니다. "
                    + "최초 등록 시각은 shippedAt으로 고정됩니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "운송장 등록 및 주문 발송 처리", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비활성 택배사 코드 또는 요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 판매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "발송할 수 없는 주문 상태")
            }
    )
    public ApiResponse<ShipmentResponse> register(
            @CurrentUserId UUID sellerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody TrackingRequest request
    ) {
        return ApiResponse.success(ShipmentResponse.from(shipmentService.register(
                sellerId, orderId, request.carrierCode(), request.trackingNumber())));
    }

    @PatchMapping("/orders/{orderId}/shipment")
    @Operation(
            summary = "운송장 수정",
            description = "판매자가 기존 운송장 정보를 수정합니다. 수정해도 shippedAt은 변경되지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "운송장 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비활성 택배사 코드 또는 요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 판매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배송정보를 찾을 수 없음")
            }
    )
    public ApiResponse<ShipmentResponse> update(
            @CurrentUserId UUID sellerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody TrackingRequest request
    ) {
        return ApiResponse.success(ShipmentResponse.from(shipmentService.update(
                sellerId, orderId, request.carrierCode(), request.trackingNumber())));
    }

    @GetMapping("/orders/{orderId}/shipment")
    @Operation(
            summary = "배송 상세와 상태 이력 조회",
            description = "주문 참여자가 정규화된 배송 상태와 중복 제거된 상태 이력을 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "배송 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 참여자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배송정보를 찾을 수 없음")
            }
    )
    public ApiResponse<ShipmentDetailResponse> detail(
            @CurrentUserId UUID actorId,
            @PathVariable UUID orderId
    ) {
        Shipment shipment = shipmentService.getForParticipant(actorId, orderId);
        List<EventResponse> events = shipmentService.timeline(actorId, orderId).stream()
                .map(EventResponse::from).toList();
        return ApiResponse.success(new ShipmentDetailResponse(ShipmentResponse.from(shipment), events));
    }

    @PostMapping("/orders/{orderId}/auto-confirm/hold")
    @Operation(
            summary = "자동 구매확정 보류",
            description = "구매자가 배송 완료 주문의 자동 구매확정을 보류합니다. 관리자 해제 전까지 워커가 확정하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자동확정 보류 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "보류할 수 없는 주문 또는 배송 상태")
            }
    )
    public ApiResponse<Void> hold(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        autoConfirmationService.hold(buyerId, orderId);
        return ApiResponse.empty();
    }

    public record TrackingRequest(
            @Schema(description = "활성 택배사 코드", example = "04")
            @NotBlank @Size(max = 40) String carrierCode,
            @Schema(description = "운송장 번호", example = "123456789012")
            @NotBlank @Size(max = 100) String trackingNumber
    ) {
    }

    public record ShipmentResponse(
            UUID id,
            UUID orderId,
            String carrierCode,
            String trackingNumber,
            ShipmentStatus status,
            @Schema(description = "최초 운송장 등록 시각(UTC). 운송장 수정 시에도 변경되지 않습니다.", example = "2026-09-02T03:15:00Z")
            Instant shippedAt,
            Instant deliveredAt,
            boolean autoConfirmHeld
    ) {
        static ShipmentResponse from(Shipment shipment) {
            return new ShipmentResponse(
                    shipment.getId(), shipment.getOrderId(), shipment.getCarrierCode(),
                    shipment.getTrackingNumber(), shipment.getStatus(), shipment.getShippedAt(),
                    shipment.getDeliveredAt(),
                    shipment.isAutoConfirmHeld());
        }
    }

    public record ShipmentDetailResponse(ShipmentResponse shipment, List<EventResponse> events) {
    }

    public record EventResponse(
            UUID id,
            ShipmentStatus status,
            String description,
            Instant eventAt
    ) {
        static EventResponse from(ShipmentEvent event) {
            return new EventResponse(event.getId(), event.getStatus(), event.getDescription(), event.getEventAt());
        }
    }
}
