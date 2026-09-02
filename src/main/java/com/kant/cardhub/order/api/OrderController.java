package com.kant.cardhub.order.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.order.application.OrderApplicationService;
import com.kant.cardhub.order.application.OrderConfirmationService;
import com.kant.cardhub.order.application.port.OrderDeliveryProjectionPort;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItem;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.domain.ShippingAddressSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderApplicationService orderService;
    private final OrderConfirmationService confirmationService;

    public OrderController(OrderApplicationService orderService, OrderConfirmationService confirmationService) {
        this.orderService = orderService;
        this.confirmationService = confirmationService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createSingle(
            @CurrentUserId UUID buyerId,
            @Valid @RequestBody SingleOrderRequest request
    ) {
        Order order = request.usesStructuredAddress()
                ? orderService.createSingle(
                        buyerId, request.listingId(), request.offerId(), request.shippingAddressSnapshot().toDomain())
                : orderService.createSingle(
                        buyerId, request.listingId(), request.offerId(), request.shippingAddress());
        return ApiResponse.success(OrderResponse.from(order));
    }

    @PostMapping("/orders/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createGroup(
            @CurrentUserId UUID buyerId,
            @Valid @RequestBody GroupOrderRequest request
    ) {
        return ApiResponse.success(OrderResponse.from(
                request.usesStructuredAddress()
                        ? orderService.create(buyerId, request.listingIds(), request.shippingAddressSnapshot().toDomain())
                        : orderService.create(buyerId, request.listingIds(), request.shippingAddress())));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderResponse> detail(@CurrentUserId UUID actorId, @PathVariable UUID orderId) {
        OrderApplicationService.OrderDetail detail = orderService.getDetailForParticipant(actorId, orderId);
        return ApiResponse.success(OrderResponse.from(
                detail.order(), detail.legacyShippingAddress(), detail.shippingAddress(),
                detail.deliveryProjection()));
    }

    @GetMapping("/users/me/orders")
    public ApiResponse<List<OrderSummary>> purchases(
            @CurrentUserId UUID buyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(orderService.purchases(buyerId, page, size).stream()
                .map(OrderSummary::from).toList());
    }

    @GetMapping("/users/me/sales")
    public ApiResponse<List<OrderSummary>> sales(
            @CurrentUserId UUID sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(orderService.sales(sellerId, page, size).stream()
                .map(OrderSummary::from).toList());
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Void> cancel(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        orderService.cancel(buyerId, orderId);
        return ApiResponse.empty();
    }

    @PostMapping("/orders/{orderId}/confirm")
    public ApiResponse<ConfirmationResponse> confirm(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        return ApiResponse.success(new ConfirmationResponse(
                orderId, confirmationService.confirm(buyerId, orderId)));
    }

    public record SingleOrderRequest(
            @NotNull UUID listingId,
            UUID offerId,
            @Size(max = 2000) String shippingAddress,
            @Valid ShippingAddressRequest shippingAddressSnapshot
    ) {
        public SingleOrderRequest(UUID listingId, UUID offerId, String shippingAddress) {
            this(listingId, offerId, shippingAddress, null);
        }

        @AssertTrue(message = "exactly one of shippingAddress and shippingAddressSnapshot is required")
        public boolean hasExactlyOneAddress() {
            return (shippingAddress != null && !shippingAddress.isBlank()) ^ (shippingAddressSnapshot != null);
        }

        boolean usesStructuredAddress() {
            return shippingAddressSnapshot != null;
        }

    }

    public record GroupOrderRequest(
            @NotEmpty @Size(max = 50) List<@NotNull UUID> listingIds,
            @Size(max = 2000) String shippingAddress,
            @Valid ShippingAddressRequest shippingAddressSnapshot
    ) {
        public GroupOrderRequest(List<UUID> listingIds, String shippingAddress) {
            this(listingIds, shippingAddress, null);
        }

        @AssertTrue(message = "exactly one of shippingAddress and shippingAddressSnapshot is required")
        public boolean hasExactlyOneAddress() {
            return (shippingAddress != null && !shippingAddress.isBlank()) ^ (shippingAddressSnapshot != null);
        }

        boolean usesStructuredAddress() {
            return shippingAddressSnapshot != null;
        }
    }

    public record ShippingAddressRequest(
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Size(max = 30) String phoneNumber,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 500) String addressLine1,
            @Size(max = 500) String addressLine2
    ) {
        ShippingAddressSnapshot toDomain() {
            return new ShippingAddressSnapshot(
                    recipientName, phoneNumber, postalCode, addressLine1, addressLine2);
        }
    }

    public record ConfirmationResponse(UUID orderId, List<UUID> tradeIds) {
    }

    public record OrderItemResponse(
            UUID id,
            UUID listingId,
            UUID cardVariantId,
            String listingTitle,
            long price,
            UUID sourceOfferId,
            String cardCondition,
            long shippingFee,
            String primaryImageStorageKey
    ) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getId(), item.getListingId(), item.getCardVariantId(),
                    item.getListingTitle(), item.getUnitPrice(), item.getSourceOfferId(),
                    item.getCardConditionSnapshot(), item.getShippingFeeSnapshot(),
                    item.getPrimaryImageStorageKey());
        }
    }

    public record OrderResponse(
            UUID id,
            String orderNo,
            UUID buyerId,
            UUID sellerId,
            long itemSubtotal,
            long shippingFee,
            long totalAmount,
            OrderStatus status,
            Instant reservationExpiresAt,
            Instant confirmedAt,
            String shippingAddress,
            ShippingAddressSnapshot shippingAddressSnapshot,
            FulfillmentStatus fulfillmentStatus,
            AutoConfirmationStatus autoConfirmationStatus,
            Instant autoConfirmAt,
            Instant autoConfirmNoticeAt,
            List<OrderItemResponse> items
    ) {
        static OrderResponse from(Order order) {
            return from(order, null, null, null);
        }

        static OrderResponse from(
                Order order,
                String shippingAddress,
                ShippingAddressSnapshot shippingAddressSnapshot,
                OrderDeliveryProjectionPort.DeliveryProjection deliveryProjection
        ) {
            FulfillmentStatus fulfillmentStatus = FulfillmentStatus.from(order.getStatus(), deliveryProjection);
            AutoConfirmationStatus autoStatus = AutoConfirmationStatus.from(order.getStatus(), deliveryProjection);
            Instant deliveredAt = deliveryProjection == null ? null : deliveryProjection.deliveredAt();
            return new OrderResponse(
                    order.getId(), order.getOrderNo(), order.getBuyerId(), order.getSellerId(), order.getItemSubtotal(),
                    order.getShippingFee(), order.getTotalAmount(), order.getStatus(),
                    order.getReservationExpiresAt(), order.getConfirmedAt(),
                    shippingAddress, shippingAddressSnapshot,
                    fulfillmentStatus, autoStatus,
                    deliveredAt == null ? null : deliveredAt.plus(Shipment.AUTO_CONFIRM_DELAY),
                    deliveredAt == null ? null : deliveredAt.plus(Shipment.AUTO_CONFIRM_NOTICE_DELAY),
                    order.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    public enum FulfillmentStatus {
        NOT_STARTED,
        PREPARING,
        SHIPPED,
        DELIVERED;

        static FulfillmentStatus from(
                OrderStatus status,
                OrderDeliveryProjectionPort.DeliveryProjection deliveryProjection
        ) {
            if (deliveryProjection != null) {
                return switch (deliveryProjection.shipmentStatus()) {
                    case "DELIVERED" -> DELIVERED;
                    case "READY", "IN_TRANSIT", "EXCEPTION" -> SHIPPED;
                    default -> PREPARING;
                };
            }
            return switch (status) {
                case PAID, REFUND_REQUESTED -> PREPARING;
                case SHIPPED -> SHIPPED;
                case DELIVERED, CONFIRMED -> DELIVERED;
                default -> NOT_STARTED;
            };
        }
    }

    public enum AutoConfirmationStatus {
        NONE,
        SCHEDULED,
        HOLD,
        COMPLETED;

        static AutoConfirmationStatus from(
                OrderStatus status,
                OrderDeliveryProjectionPort.DeliveryProjection projection
        ) {
            if (status == OrderStatus.CONFIRMED) {
                return COMPLETED;
            }
            if (projection == null || projection.deliveredAt() == null) {
                return NONE;
            }
            return projection.autoConfirmHeld() ? HOLD : SCHEDULED;
        }
    }

    public record OrderSummary(
            UUID id,
            String orderNo,
            UUID buyerId,
            UUID sellerId,
            long totalAmount,
            OrderStatus status,
            Instant createdAt
    ) {
        static OrderSummary from(Order order) {
            return new OrderSummary(
                    order.getId(), order.getOrderNo(), order.getBuyerId(), order.getSellerId(),
                    order.getTotalAmount(), order.getStatus(), order.getCreatedAt());
        }
    }
}
