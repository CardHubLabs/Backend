package com.kant.cardhub.order.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.order.application.OrderApplicationService;
import com.kant.cardhub.order.application.OrderConfirmationService;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItem;
import com.kant.cardhub.order.domain.OrderStatus;
import jakarta.validation.Valid;
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
        return ApiResponse.success(OrderResponse.from(orderService.createSingle(
                buyerId, request.listingId(), request.offerId(), request.shippingAddress())));
    }

    @PostMapping("/orders/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createGroup(
            @CurrentUserId UUID buyerId,
            @Valid @RequestBody GroupOrderRequest request
    ) {
        return ApiResponse.success(OrderResponse.from(
                orderService.create(buyerId, request.listingIds(), request.shippingAddress())));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderResponse> detail(@CurrentUserId UUID actorId, @PathVariable UUID orderId) {
        OrderApplicationService.OrderDetail detail = orderService.getDetailForParticipant(actorId, orderId);
        return ApiResponse.success(OrderResponse.from(detail.order(), detail.shippingAddress()));
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
            @NotBlank @Size(max = 2000) String shippingAddress
    ) {
    }

    public record GroupOrderRequest(
            @NotEmpty @Size(max = 50) List<@NotNull UUID> listingIds,
            @NotBlank @Size(max = 2000) String shippingAddress
    ) {
    }

    public record ConfirmationResponse(UUID orderId, List<UUID> tradeIds) {
    }

    public record OrderItemResponse(
            UUID id,
            UUID listingId,
            UUID cardVariantId,
            String listingTitle,
            long price
    ) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getId(), item.getListingId(), item.getCardVariantId(),
                    item.getListingTitle(), item.getUnitPrice());
        }
    }

    public record OrderResponse(
            UUID id,
            UUID buyerId,
            UUID sellerId,
            long itemSubtotal,
            long shippingFee,
            long totalAmount,
            OrderStatus status,
            Instant reservationExpiresAt,
            Instant confirmedAt,
            String shippingAddress,
            List<OrderItemResponse> items
    ) {
        static OrderResponse from(Order order) {
            return from(order, null);
        }

        static OrderResponse from(Order order, String shippingAddress) {
            return new OrderResponse(
                    order.getId(), order.getBuyerId(), order.getSellerId(), order.getItemSubtotal(),
                    order.getShippingFee(), order.getTotalAmount(), order.getStatus(),
                    order.getReservationExpiresAt(), order.getConfirmedAt(),
                    shippingAddress,
                    order.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record OrderSummary(
            UUID id,
            UUID buyerId,
            UUID sellerId,
            long totalAmount,
            OrderStatus status,
            Instant createdAt
    ) {
        static OrderSummary from(Order order) {
            return new OrderSummary(
                    order.getId(), order.getBuyerId(), order.getSellerId(),
                    order.getTotalAmount(), order.getStatus(), order.getCreatedAt());
        }
    }
}
