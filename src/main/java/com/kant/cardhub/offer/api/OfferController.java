package com.kant.cardhub.offer.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.offer.application.OfferRole;
import com.kant.cardhub.offer.application.OfferService;
import com.kant.cardhub.offer.application.OfferView;
import com.kant.cardhub.offer.domain.OfferDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/listings/{listingId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OfferView.Detail> create(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId,
            @Valid @RequestBody CreateOfferRequest request
    ) {
        return ApiResponse.success(offerService.create(userId, listingId, request.price()));
    }

    @PostMapping("/offers/{offerId}/respond")
    public ApiResponse<OfferView.Detail> respond(
            @CurrentUserId UUID userId,
            @PathVariable UUID offerId,
            @Valid @RequestBody RespondOfferRequest request
    ) {
        return ApiResponse.success(offerService.respond(
                userId, offerId, request.action(), request.counterPrice()
        ));
    }

    @GetMapping("/offers/{offerId}")
    public ApiResponse<OfferView.WithHistory> detail(
            @CurrentUserId UUID userId,
            @PathVariable UUID offerId
    ) {
        return ApiResponse.success(offerService.detail(userId, offerId));
    }

    @GetMapping("/users/me/offers")
    public ApiResponse<OfferView.Page<OfferView.Detail>> mine(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "BUYER") OfferRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(offerService.mine(userId, role, page, size));
    }

    public record CreateOfferRequest(@Min(500) long price) {}

    public record RespondOfferRequest(
            @NotNull OfferDecision action,
            @Min(500) Long counterPrice
    ) {}
}
