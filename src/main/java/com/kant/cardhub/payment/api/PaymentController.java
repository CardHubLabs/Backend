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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Payments", description = "Toss 결제 확인과 구매자 환불 요청·상태 조회 API")
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
    @Operation(
            summary = "Toss 결제 확인",
            description = "주문 금액과 Toss orderId를 외부 호출 전에 검증하고 Idempotency-Key 단위로 확인합니다. "
                    + "결과가 불명확하거나 다른 요청이 확인 중이면 202와 UNKNOWN/CONFIRMING 상태를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "결제 승인 또는 확정 실패 결과", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "202", description = "결과 확인 중 또는 UNKNOWN 복구 대기", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수 헤더 또는 요청 형식 검증 실패"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 멱등 키에 다른 payload 사용 또는 주문 상태 충돌"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "주문 금액 또는 Toss orderId 불일치")
            }
    )
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
    @Operation(
            summary = "주문 결제 상태 조회",
            description = "구매자가 주문의 내부 결제 상태와 공급자 검증 결과를 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "결제 상태 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "결제 또는 주문을 찾을 수 없음")
            }
    )
    public ApiResponse<PaymentResult> payment(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        return ApiResponse.success(paymentService.getForOrder(buyerId, orderId));
    }

    @PostMapping("/orders/{orderId}/refund-request")
    @Operation(
            summary = "배송 전 환불 요청",
            description = "구매자가 배송 전 PAID 주문의 전체 환불을 요청합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "환불 요청 접수", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "환불 요청이 불가능한 주문 상태")
            }
    )
    public ApiResponse<RefundResponse> requestRefund(
            @CurrentUserId UUID buyerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody RefundRequest request
    ) {
        return ApiResponse.success(RefundResponse.from(
                refundRequestService.request(buyerId, orderId, request.reason())));
    }

    @GetMapping("/orders/{orderId}/refund")
    @Operation(
            summary = "구매자 환불 진행 상태 조회",
            description = "구매자가 자신의 주문에 연결된 환불 요청과 처리 상태를 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "환불 상태 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "환불 요청을 찾을 수 없음")
            }
    )
    public ApiResponse<RefundResponse> refund(
            @CurrentUserId UUID buyerId,
            @PathVariable UUID orderId
    ) {
        return ApiResponse.success(RefundResponse.from(
                refundRequestService.findForBuyer(buyerId, orderId)));
    }

    public record ConfirmPaymentRequest(
            @Schema(description = "CardHub 주문 ID", example = "5c766882-1392-4c6e-8d28-c88600845d8e")
            @NotNull UUID orderId,
            @Schema(description = "Toss가 발급한 결제 키", example = "tgen_202609020001")
            @NotBlank @Size(max = 200) String paymentKey,
            @Schema(description = "주문 생성 시 받은 CardHub orderNo", example = "CH-5c76688213924c6e8d28c88600845d8e")
            @NotBlank @Size(max = 64) String tossOrderId,
            @Schema(description = "서버 주문 총액(원)", example = "35000")
            @Positive long amount
    ) {
    }

    public record RefundRequest(
            @Schema(description = "환불 요청 사유", example = "판매자 발송 전 구매 의사를 철회합니다.")
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record RefundResponse(UUID id, UUID orderId, long amount, RefundStatus status) {
        static RefundResponse from(Refund refund) {
            return new RefundResponse(refund.getId(), refund.getOrderId(), refund.getAmount(), refund.getStatus());
        }
    }

}
