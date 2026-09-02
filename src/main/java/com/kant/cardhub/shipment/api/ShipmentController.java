package com.kant.cardhub.shipment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.shipment.application.ShipmentApplicationService;
import com.kant.cardhub.shipment.application.ShipmentAutoConfirmationService;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentEvent;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
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
    public ApiResponse<ShipmentResponse> register(
            @CurrentUserId UUID sellerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody TrackingRequest request
    ) {
        return ApiResponse.success(ShipmentResponse.from(shipmentService.register(
                sellerId, orderId, request.carrierCode(), request.trackingNumber())));
    }

    @PatchMapping("/orders/{orderId}/shipment")
    public ApiResponse<ShipmentResponse> update(
            @CurrentUserId UUID sellerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody TrackingRequest request
    ) {
        return ApiResponse.success(ShipmentResponse.from(shipmentService.update(
                sellerId, orderId, request.carrierCode(), request.trackingNumber())));
    }

    @GetMapping("/orders/{orderId}/shipment")
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
    public ApiResponse<Void> hold(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        autoConfirmationService.hold(buyerId, orderId);
        return ApiResponse.empty();
    }

    public record TrackingRequest(
            @NotBlank @Size(max = 40) String carrierCode,
            @NotBlank @Size(max = 100) String trackingNumber
    ) {
    }

    public record ShipmentResponse(
            UUID id,
            UUID orderId,
            String carrierCode,
            String trackingNumber,
            ShipmentStatus status,
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
