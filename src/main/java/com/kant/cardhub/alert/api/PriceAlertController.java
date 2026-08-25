package com.kant.cardhub.alert.api;

import com.kant.cardhub.alert.application.PriceAlertService;
import com.kant.cardhub.alert.domain.PriceAlert;
import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PriceAlertResponse> create(
            @CurrentUserId UUID userId,
            @Valid @RequestBody CreatePriceAlertRequest request
    ) {
        return ApiResponse.success(PriceAlertResponse.from(priceAlertService.create(
                userId, request.listingId(), request.thresholdPercentage()
        )));
    }

    @DeleteMapping("/{alertId}")
    public ApiResponse<Void> cancel(
            @CurrentUserId UUID userId,
            @PathVariable UUID alertId
    ) {
        priceAlertService.cancel(userId, alertId);
        return ApiResponse.empty();
    }

    @GetMapping
    public ApiResponse<PriceAlertPageResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PriceAlertPageResponse.from(priceAlertService.list(userId, page, size)));
    }

    public record CreatePriceAlertRequest(
            @NotNull UUID listingId,
            @Min(1) @Max(99) Integer thresholdPercentage
    ) {
    }

    public record PriceAlertResponse(
            UUID id,
            UUID listingId,
            long baselinePrice,
            int thresholdPercentage,
            boolean active,
            Long triggeredPrice,
            Instant triggeredAt,
            Instant createdAt
    ) {
        static PriceAlertResponse from(PriceAlert alert) {
            return new PriceAlertResponse(
                    alert.getId(), alert.getListingId(), alert.getBaselinePrice(),
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
