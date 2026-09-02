package com.kant.cardhub.admin.api;

import com.kant.cardhub.admin.application.AdminService;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingModerationStatus;
import com.kant.cardhub.listing.domain.ListingStatus;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserRole;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.trade.domain.Trade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/users/{userId}/suspend")
    public ApiResponse<Void> suspendUser(
            @CurrentUserId UUID adminId,
            @PathVariable UUID userId
    ) {
        adminService.suspendUser(adminId, userId);
        return ApiResponse.empty();
    }

    @PostMapping("/users/{userId}/activate")
    public ApiResponse<Void> activateUser(
            @CurrentUserId UUID adminId,
            @PathVariable UUID userId
    ) {
        adminService.activateUser(adminId, userId);
        return ApiResponse.empty();
    }

    @PostMapping("/listings/{listingId}/hide")
    public ApiResponse<Void> hideListing(
            @CurrentUserId UUID adminId,
            @PathVariable UUID listingId
    ) {
        adminService.hideListing(adminId, listingId);
        return ApiResponse.empty();
    }

    @PostMapping("/listings/{listingId}/block")
    public ApiResponse<Void> blockListing(
            @CurrentUserId UUID adminId,
            @PathVariable UUID listingId,
            @Valid @RequestBody BlockListingRequest request
    ) {
        adminService.blockListing(adminId, listingId, request.reason());
        return ApiResponse.empty();
    }

    @PostMapping("/listings/{listingId}/unblock")
    public ApiResponse<Void> unblockListing(
            @CurrentUserId UUID adminId,
            @PathVariable UUID listingId
    ) {
        adminService.unblockListing(adminId, listingId);
        return ApiResponse.empty();
    }

    @PostMapping("/refunds/{refundId}/approve")
    public ResponseEntity<ApiResponse<RefundApprovalResponse>> approveRefund(
            @CurrentUserId UUID adminId,
            @PathVariable UUID refundId
    ) {
        RefundStatus status = adminService.approveRefund(adminId, refundId);
        HttpStatus httpStatus = status == RefundStatus.UNKNOWN ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.success(new RefundApprovalResponse(status)));
    }

    @PostMapping("/orders/{orderId}/auto-confirm/release")
    public ApiResponse<Void> releaseAutoConfirmHold(
            @CurrentUserId UUID adminId,
            @PathVariable UUID orderId
    ) {
        adminService.releaseAutoConfirmHold(adminId, orderId);
        return ApiResponse.empty();
    }

    @PostMapping("/orders/{orderId}/shipment/delivered")
    public ApiResponse<Void> markShipmentDelivered(
            @CurrentUserId UUID adminId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) DeliveredRequest request
    ) {
        adminService.markShipmentDelivered(adminId, orderId, request == null ? null : request.deliveredAt());
        return ApiResponse.empty();
    }

    @PostMapping("/trades/{tradeId}/exclude")
    public ApiResponse<Void> excludeTrade(
            @CurrentUserId UUID adminId,
            @PathVariable UUID tradeId,
            @Valid @RequestBody ExcludeTradeRequest request
    ) {
        adminService.excludeTrade(adminId, tradeId, request.reason());
        return ApiResponse.empty();
    }

    @PostMapping("/trades/{tradeId}/restore")
    public ApiResponse<Void> restoreTrade(
            @CurrentUserId UUID adminId,
            @PathVariable UUID tradeId
    ) {
        adminService.restoreTrade(adminId, tradeId);
        return ApiResponse.empty();
    }

    @GetMapping("/users")
    public ApiResponse<PagePayload<UserResponse>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.users(page, size), UserResponse::from));
    }

    @GetMapping("/listings")
    public ApiResponse<PagePayload<ListingResponse>> listings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.listings(page, size), ListingResponse::from));
    }

    @GetMapping("/orders")
    public ApiResponse<PagePayload<OrderResponse>> orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.orders(page, size), OrderResponse::from));
    }

    @GetMapping("/payments")
    public ApiResponse<PagePayload<PaymentResponse>> payments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.payments(page, size), PaymentResponse::from));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentResponse> payment(@PathVariable UUID paymentId) {
        return ApiResponse.success(PaymentResponse.from(adminService.payment(paymentId)));
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    public ApiResponse<PaymentReconcileResponse> reconcilePayment(
            @CurrentUserId UUID adminId,
            @PathVariable UUID paymentId
    ) {
        return ApiResponse.success(new PaymentReconcileResponse(
                adminService.enqueuePaymentReconcile(adminId, paymentId)));
    }

    @GetMapping("/refunds")
    public ApiResponse<PagePayload<RefundResponse>> refunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.refunds(page, size), RefundResponse::from));
    }

    @GetMapping("/trades")
    public ApiResponse<PagePayload<TradeResponse>> trades(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PagePayload.from(adminService.trades(page, size), TradeResponse::from));
    }

    public record UserResponse(
            UUID id,
            String email,
            String nickname,
            UserRole role,
            UserStatus status,
            Instant createdAt
    ) {
        static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(), user.getEmail(), user.getNickname(),
                    user.getRole(), user.getStatus(), user.getCreatedAt()
            );
        }
    }

    public record ListingResponse(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            long price,
            long shippingFee,
            ListingStatus status,
            ListingModerationStatus moderationStatus,
            Instant blockedAt,
            UUID blockedBy,
            String blockedReason,
            Instant createdAt
    ) {
        static ListingResponse from(Listing listing) {
            return new ListingResponse(
                    listing.getId(), listing.getSellerId(), listing.getCardVariantId(),
                    listing.getPrice(), listing.getShippingFee(), listing.getStatus(),
                    listing.getModerationStatus(), listing.getBlockedAt(), listing.getBlockedBy(),
                    listing.getBlockedReason(), listing.getCreatedAt()
            );
        }
    }

    public record BlockListingRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record OrderResponse(
            UUID id,
            UUID buyerId,
            UUID sellerId,
            long totalAmount,
            OrderStatus status,
            Instant createdAt
    ) {
        static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(), order.getBuyerId(), order.getSellerId(),
                    order.getTotalAmount(), order.getStatus(), order.getCreatedAt()
            );
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            UUID buyerId,
            long amount,
            PaymentStatus status,
            String providerCode,
            String providerStatus,
            com.kant.cardhub.payment.domain.PaymentMethod paymentMethod,
            String easyPayProvider,
            int confirmAttemptCount,
            Instant lastConfirmAttemptAt,
            Instant nextReconcileAt,
            boolean manualReviewRequired,
            Instant approvedAt
    ) {
        static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.getId(), payment.getOrderId(), payment.getBuyerId(), payment.getAmount(),
                    payment.getStatus(), payment.getProviderCode(), payment.getProviderStatus(),
                    payment.getPaymentMethod(), payment.getEasyPayProvider(),
                    payment.getConfirmAttemptCount(), payment.getLastConfirmAttemptAt(),
                    payment.getNextReconcileAt(), payment.isManualReviewRequired(),
                    payment.getApprovedAt()
            );
        }
    }

    public record PaymentReconcileResponse(UUID jobId) {
    }

    public record RefundResponse(
            UUID id,
            UUID orderId,
            UUID buyerId,
            long amount,
            RefundStatus status,
            String reason
    ) {
        static RefundResponse from(Refund refund) {
            return new RefundResponse(
                    refund.getId(), refund.getOrderId(), refund.getBuyerId(), refund.getAmount(),
                    refund.getStatus(), refund.getReason()
            );
        }
    }

    public record RefundApprovalResponse(RefundStatus status) {
    }

    public record DeliveredRequest(Instant deliveredAt) {
    }

    public record ExcludeTradeRequest(@NotBlank @Size(max = 100) String reason) {
    }

    public record TradeResponse(
            UUID id,
            UUID orderId,
            UUID orderItemId,
            UUID listingId,
            UUID cardVariantId,
            long price,
            Instant occurredAt,
            boolean excluded
    ) {
        static TradeResponse from(Trade trade) {
            return new TradeResponse(
                    trade.getId(), trade.getOrderId(), trade.getOrderItemId(), trade.getListingId(),
                    trade.getCardVariantId(), trade.getPrice(), trade.getOccurredAt(), trade.isExcluded()
            );
        }
    }

    public record PagePayload<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        static <S, T> PagePayload<T> from(Page<S> source, Function<S, T> mapper) {
            return new PagePayload<>(
                    source.getContent().stream().map(mapper).toList(),
                    source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages()
            );
        }
    }
}
