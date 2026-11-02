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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "동일 판매자 단일·묶음 주문과 구매확정 API")
public class OrderController {

    private final OrderApplicationService orderService;
    private final OrderConfirmationService confirmationService;

    public OrderController(OrderApplicationService orderService, OrderConfirmationService confirmationService) {
        this.orderService = orderService;
        this.confirmationService = confirmationService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "단일 매물 주문 생성",
            description = "매물을 10분간 원자적으로 예약합니다. shippingAddressSnapshot과 legacy shippingAddress 중 "
                    + "정확히 하나만 전달해야 합니다. 수락된 제안은 단일 주문에서만 사용할 수 있습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "주문 및 매물 예약 생성", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "주소 XOR 또는 요청 형식 검증 실패"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물 또는 제안을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "매물이 이미 예약되었거나 판매됨"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "제안 사용 조건 또는 주문 정책 위반")
            }
    )
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
    @Operation(
            summary = "동일 판매자 묶음 주문 생성",
            description = "같은 판매자의 매물만 하나의 주문으로 묶고 모든 매물을 원자적으로 예약합니다. "
                    + "shippingAddressSnapshot과 legacy shippingAddress 중 정확히 하나만 전달해야 합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "묶음 주문 및 전체 예약 생성", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "주소 XOR, 중복 항목 또는 요청 형식 검증 실패"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "하나 이상의 매물을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "하나 이상의 매물을 예약할 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "판매자가 서로 다르거나 주문 정책 위반")
            }
    )
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
    @Operation(
            summary = "주문 상세 조회",
            description = "주문 참여자만 조회할 수 있습니다. fulfillmentStatus와 autoConfirmationStatus는 "
                    + "주문·배송 상태에서 계산한 화면용 파생 상태입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "주문 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 참여자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
            }
    )
    public ApiResponse<OrderResponse> detail(@CurrentUserId UUID actorId, @PathVariable UUID orderId) {
        OrderApplicationService.OrderDetail detail = orderService.getDetailForParticipant(actorId, orderId);
        return ApiResponse.success(OrderResponse.from(
                detail.order(), detail.legacyShippingAddress(), detail.shippingAddress(),
                detail.deliveryProjection()));
    }

    @GetMapping("/users/me/orders")
    @Operation(summary = "내 구매내역 조회", description = "현재 사용자의 구매 주문을 최신순으로 조회합니다.")
    public ApiResponse<List<OrderSummary>> purchases(
            @CurrentUserId UUID buyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(orderService.purchases(buyerId, page, size).stream()
                .map(OrderSummary::from).toList());
    }

    @GetMapping("/users/me/sales")
    @Operation(summary = "내 판매내역 조회", description = "현재 사용자의 판매 주문을 최신순으로 조회합니다.")
    public ApiResponse<List<OrderSummary>> sales(
            @CurrentUserId UUID sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(orderService.sales(sellerId, page, size).stream()
                .map(OrderSummary::from).toList());
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(
            summary = "결제 전 주문 취소",
            description = "구매자가 PAYMENT_PENDING 주문을 취소하고 자신이 소유한 예약만 해제합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 취소 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "취소할 수 없는 주문 상태")
            }
    )
    public ApiResponse<Void> cancel(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        orderService.cancel(buyerId, orderId);
        return ApiResponse.empty();
    }

    @PostMapping("/orders/{orderId}/confirm")
    @Operation(
            summary = "구매확정",
            description = "배송 완료 주문을 구매확정하고 주문 항목별 거래 ID를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "구매확정 및 거래 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "주문 구매자가 아님"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "확정할 수 없는 주문 상태")
            }
    )
    public ApiResponse<ConfirmationResponse> confirm(@CurrentUserId UUID buyerId, @PathVariable UUID orderId) {
        return ApiResponse.success(new ConfirmationResponse(
                orderId, confirmationService.confirm(buyerId, orderId)));
    }

    @Schema(description = "단일 주문 요청. shippingAddress와 shippingAddressSnapshot 중 정확히 하나만 허용합니다.")
    public record SingleOrderRequest(
            @Schema(description = "주문할 매물 ID", example = "786d3e18-ab50-4573-acd4-012356c4fe04")
            @NotNull UUID listingId,
            @Schema(description = "수락된 제안 ID", nullable = true, example = "35530666-e730-43f4-b474-a46619c02187")
            UUID offerId,
            @Schema(
                    description = "호환용 평문 주소 입력. 신규 요청에서는 shippingAddressSnapshot을 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "서울특별시 중구 세종대로 110"
            ) @Size(max = 2000) String shippingAddress,
            @Schema(description = "구조화 배송지. shippingAddress와 동시에 전달할 수 없습니다.")
            @Valid ShippingAddressRequest shippingAddressSnapshot
    ) {
        @Deprecated(since = "v1", forRemoval = false)
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

    @Schema(description = "묶음 주문 요청. shippingAddress와 shippingAddressSnapshot 중 정확히 하나만 허용합니다.")
    public record GroupOrderRequest(
            @Schema(description = "같은 판매자의 매물 ID 목록", example = "[\"786d3e18-ab50-4573-acd4-012356c4fe04\"]")
            @NotEmpty @Size(max = 50) List<@NotNull UUID> listingIds,
            @Schema(
                    description = "호환용 평문 주소 입력. 신규 요청에서는 shippingAddressSnapshot을 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "서울특별시 중구 세종대로 110"
            ) @Size(max = 2000) String shippingAddress,
            @Schema(description = "구조화 배송지. shippingAddress와 동시에 전달할 수 없습니다.")
            @Valid ShippingAddressRequest shippingAddressSnapshot
    ) {
        @Deprecated(since = "v1", forRemoval = false)
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
            @Schema(description = "수령인 이름", example = "홍길동")
            @NotBlank @Size(max = 100) String recipientName,
            @Schema(description = "수령인 연락처", example = "010-1234-5678")
            @NotBlank @Size(max = 30) String phoneNumber,
            @Schema(description = "우편번호", example = "04524")
            @NotBlank @Size(max = 20) String postalCode,
            @Schema(description = "기본 주소", example = "서울특별시 중구 세종대로 110")
            @NotBlank @Size(max = 500) String addressLine1,
            @Schema(description = "상세 주소", nullable = true, example = "10층")
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
            @Schema(
                    description = "v1 주문의 호환용 배송지. 신규 응답에서는 shippingAddressSnapshot을 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "서울특별시 중구 세종대로 110"
            ) String shippingAddress,
            @Schema(description = "복호화된 구조화 배송지. 주문 참여자와 주문 상태에 따라 null일 수 있습니다.")
            ShippingAddressSnapshot shippingAddressSnapshot,
            @Schema(description = "주문과 배송 상태에서 계산한 화면용 배송 진행 상태")
            FulfillmentStatus fulfillmentStatus,
            @Schema(description = "배송 완료 시각과 hold 여부에서 계산한 자동 구매확정 상태")
            AutoConfirmationStatus autoConfirmationStatus,
            @Schema(description = "예정된 자동 구매확정 시각(UTC)", nullable = true, example = "2026-09-09T03:15:00Z")
            Instant autoConfirmAt,
            @Schema(description = "자동 구매확정 사전 알림 예정 시각(UTC)", nullable = true, example = "2026-09-08T03:15:00Z")
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
