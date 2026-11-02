package com.kant.cardhub.admin.api;

import com.kant.cardhub.admin.application.AdminService;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingEffectiveStatus;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin", description = "관리자 제재, 결제 복구와 운영 조회 API")
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
    @Deprecated(since = "v1", forRemoval = false)
    @Operation(
            summary = "관리자 매물 숨김 (legacy)",
            description = "호환용 API입니다. legacy 사유로 moderation 차단을 수행합니다. "
                    + "신규 운영 도구는 POST /admin/listings/{listingId}/block과 필수 사유를 사용해야 합니다.",
            deprecated = true,
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "legacy 차단 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 차단된 매물")
            }
    )
    public ApiResponse<Void> hideListing(
            @CurrentUserId UUID adminId,
            @PathVariable UUID listingId
    ) {
        adminService.hideListing(adminId, listingId);
        return ApiResponse.empty();
    }

    @PostMapping("/listings/{listingId}/block")
    @Operation(
            summary = "관리자 매물 차단",
            description = "판매 lifecycle과 독립된 moderation을 BLOCKED로 전환합니다. ACTIVE 매물은 HIDDEN으로 "
                    + "바꾸고 열린 제안과 장바구니 노출을 정리하지만 결제 중 RESERVED 예약은 유지합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "매물 차단 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "차단 사유 누락 또는 길이 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 차단된 매물")
            }
    )
    public ApiResponse<Void> blockListing(
            @CurrentUserId UUID adminId,
            @PathVariable UUID listingId,
            @Valid @RequestBody BlockListingRequest request
    ) {
        adminService.blockListing(adminId, listingId, request.reason());
        return ApiResponse.empty();
    }

    @PostMapping("/listings/{listingId}/unblock")
    @Operation(
            summary = "관리자 매물 차단 해제",
            description = "moderation만 CLEAR로 복구합니다. lifecycle이 HIDDEN이면 판매자가 별도로 활성화해야 합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "매물 차단 해제 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "차단되지 않은 매물")
            }
    )
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
    @Operation(
            summary = "관리자 매물 목록 조회",
            description = "lifecycle status와 moderationStatus를 보존하고 BLOCKED 우선 규칙의 effectiveStatus를 함께 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "관리자 매물 목록 조회 성공", useReturnTypeSchema = true)
    )
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
    @Operation(
            summary = "관리자 결제 상세 조회",
            description = "UNKNOWN 복구 메타데이터, 공급자 상태, 확인 시도 횟수와 수동 검토 여부를 조회합니다. "
                    + "결제 키와 민감 원문은 응답하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "결제 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결제를 찾을 수 없음")
            }
    )
    public ApiResponse<PaymentResponse> payment(@PathVariable UUID paymentId) {
        return ApiResponse.success(PaymentResponse.from(adminService.payment(paymentId)));
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    @Operation(
            summary = "관리자 결제 재조회 작업 등록",
            description = "UNKNOWN 결제를 같은 멱등 키로 재확인하고 공급자 조회로 검증하는 작업을 등록합니다. "
                    + "관리자가 APPROVED 상태를 강제로 지정하지는 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "재조회 작업 등록 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결제를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재조회 대상이 아닌 결제 상태 또는 중복 작업")
            }
    )
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

    @Schema(name = "AdminListingResponse", description = "관리자용 lifecycle·moderation 포함 매물 응답")
    public record ListingResponse(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            long price,
            long shippingFee,
            ListingStatus status,
            ListingModerationStatus moderationStatus,
            @Schema(
                    description = "moderation BLOCKED가 lifecycle보다 우선하며, 그 외에는 status와 동일한 유효 상태",
                    allowableValues = {"ACTIVE", "HIDDEN", "RESERVED", "SOLD", "DELETED", "BLOCKED"},
                    example = "BLOCKED"
            )
            ListingEffectiveStatus effectiveStatus,
            Instant blockedAt,
            UUID blockedBy,
            String blockedReason,
            Instant createdAt
    ) {
        static ListingResponse from(Listing listing) {
            return new ListingResponse(
                    listing.getId(), listing.getSellerId(), listing.getCardVariantId(),
                    listing.getPrice(), listing.getShippingFee(), listing.getStatus(),
                    listing.getModerationStatus(), listing.getEffectiveStatus(), listing.getBlockedAt(),
                    listing.getBlockedBy(),
                    listing.getBlockedReason(), listing.getCreatedAt()
            );
        }
    }

    public record BlockListingRequest(
            @Schema(description = "운영자 차단 사유", example = "카탈로그 정보와 다른 상품 이미지 사용")
            @NotBlank @Size(max = 500) String reason
    ) {
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
