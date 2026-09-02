package com.kant.cardhub.payment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.payment.application.PaymentApplicationService;
import com.kant.cardhub.payment.application.PaymentCommand;
import com.kant.cardhub.payment.application.PaymentResult;
import com.kant.cardhub.payment.application.RefundRequestService;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentApplicationService paymentService;
    private final RefundRequestService refundRequestService;

    public PaymentController(
            PaymentApplicationService paymentService,
            RefundRequestService refundRequestService
    ) {
        this.paymentService = paymentService;
        this.refundRequestService = refundRequestService;
    }

    @PostMapping("/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentResult>> confirm(
            @CurrentUserId UUID buyerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        PaymentResult result = paymentService.confirm(new PaymentCommand(
                buyerId, request.orderId(), request.paymentKey(), request.tossOrderId(),
                request.amount(), idempotencyKey));
        HttpStatus status = result.status() == PaymentStatus.UNKNOWN || result.status() == PaymentStatus.CONFIRMING
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }

    @GetMapping("/orders/{orderId}/payment")
    public ApiResponse<PaymentResult> payment(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        return ApiResponse.success(paymentService.getForOrder(buyerId, orderId));
    }

    @PostMapping("/orders/{orderId}/refund-request")
    public ApiResponse<RefundResponse> requestRefund(
            @CurrentUserId UUID buyerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(RefundResponse.from(
                refundRequestService.request(buyerId, orderId, request.reason())));
    }

    @GetMapping("/orders/{orderId}/refund")
    public ApiResponse<RefundResponse> refund(
            @CurrentUserId UUID buyerId,
            @PathVariable UUID orderId
    ) {
        return ApiResponse.success(RefundResponse.from(
                refundRequestService.findForBuyer(buyerId, orderId)));
    }

    public record ConfirmPaymentRequest(
            @NotNull UUID orderId,
            @NotBlank @Size(max = 200) String paymentKey,
            @NotBlank @Size(max = 64) String tossOrderId,
            @Positive long amount
    ) {
    }

    public record RefundRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record RefundResponse(UUID id, UUID orderId, long amount, RefundStatus status) {
        static RefundResponse from(Refund refund) {
            return new RefundResponse(refund.getId(), refund.getOrderId(), refund.getAmount(), refund.getStatus());
        }
    }

}
