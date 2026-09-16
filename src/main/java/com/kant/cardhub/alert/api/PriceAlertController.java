package com.kant.cardhub.alert.api;

import com.kant.cardhub.alert.application.PriceAlertService;
import com.kant.cardhub.alert.domain.PriceAlert;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/price-alerts")
@Tag(name = "Price Alerts", description = "카드 Variant 최저가 기반 가격 알림 API")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Variant 가격 알림 생성",
            description = "cardVariantId를 사용해 현재 활성 최저가를 기준가로 저장합니다. "
                    + "호환 기간에는 listingId도 허용하지만 두 필드는 정확히 하나만 전달해야 합니다. "
                    + "기준가 대비 설정 비율 이상 하락하면 한 번 알리고 비활성화합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "가격 알림 생성 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "대상 필드 XOR 또는 임계치 검증 실패"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동일 Variant의 활성 알림이 이미 존재"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "활성 매물이 없어 기준가를 계산할 수 없음")
            }
    )
    public ApiResponse<PriceAlertResponse> create(
            @CurrentUserId UUID userId,
            @Valid @RequestBody CreatePriceAlertRequest request
    ) {
        PriceAlert alert = request.cardVariantId() != null
                ? priceAlertService.createForVariant(
                        userId, request.cardVariantId(), request.thresholdPercentage())
                : priceAlertService.create(
                        userId, request.listingId(), request.thresholdPercentage());
        return ApiResponse.success(PriceAlertResponse.from(alert));
    }

    @DeleteMapping("/{alertId}")
    @Operation(
            summary = "가격 알림 취소",
            description = "사용자가 소유한 활성 가격 알림을 비활성화합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 취소 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음")
            }
    )
    public ApiResponse<Void> cancel(
            @CurrentUserId UUID userId,
            @PathVariable UUID alertId
    ) {
        priceAlertService.cancel(userId, alertId);
        return ApiResponse.empty();
    }

    @GetMapping
    @Operation(
            summary = "가격 알림 목록 조회",
            description = "현재 사용자의 가격 알림 이력을 최신순으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "가격 알림 목록 조회 성공", useReturnTypeSchema = true)
    )
    public ApiResponse<PriceAlertPageResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PriceAlertPageResponse.from(priceAlertService.list(userId, page, size)));
    }

    public record CreatePriceAlertRequest(
            @Schema(
                    description = "가격을 추적할 카드 Variant ID. listingId와 정확히 하나만 전달합니다.",
                    example = "6fd16860-e4b2-40a8-a239-b9af9bd6ff17"
            ) UUID cardVariantId,
            @Schema(
                    description = "호환용 매물 ID. 신규 요청에서는 cardVariantId를 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "786d3e18-ab50-4573-acd4-012356c4fe04"
            ) UUID listingId,
            @Schema(description = "기준가 대비 하락 알림 비율(%)", example = "10", defaultValue = "10")
            @Min(1) @Max(99) Integer thresholdPercentage
    ) {
        @Deprecated(since = "v1", forRemoval = false)
        public CreatePriceAlertRequest(UUID listingId, Integer thresholdPercentage) {
            this(null, listingId, thresholdPercentage);
        }

        @AssertTrue(message = "exactly one of cardVariantId and listingId is required")
        public boolean hasExactlyOneTarget() {
            return (cardVariantId != null) ^ (listingId != null);
        }
    }

    public record PriceAlertResponse(
            UUID id,
            UUID cardVariantId,
            @Schema(
                    description = "호환용 원본 매물 ID. 신규 응답에서는 cardVariantId를 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "786d3e18-ab50-4573-acd4-012356c4fe04"
            ) UUID listingId,
            long baselinePrice,
            int thresholdPercentage,
            boolean active,
            Long triggeredPrice,
            Instant triggeredAt,
            Instant createdAt
    ) {
        static PriceAlertResponse from(PriceAlert alert) {
            return new PriceAlertResponse(
                    alert.getId(), alert.getCardVariantId(), alert.getListingId(), alert.getBaselinePrice(),
                    alert.getThresholdPercentage(), alert.isActive(), alert.getTriggeredPrice(),
                    alert.getTriggeredAt(), alert.getCreatedAt()
            );
        }
    }

    public record PriceAlertPageResponse(
            List<PriceAlertResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        static PriceAlertPageResponse from(Page<PriceAlert> page) {
            return new PriceAlertPageResponse(
                    page.getContent().stream().map(PriceAlertResponse::from).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
            );
        }
    }
}
