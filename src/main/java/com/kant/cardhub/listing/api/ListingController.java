package com.kant.cardhub.listing.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.listing.application.ListingService;
import com.kant.cardhub.listing.application.ListingView;
import com.kant.cardhub.listing.domain.CardCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/listings")
    public ApiResponse<ListingView.Page<ListingView.Summary>> search(
            @RequestParam(required = false) UUID cardVariantId,
            @RequestParam(required = false) @Min(0) Long minPrice,
            @RequestParam(required = false) @Min(0) Long maxPrice,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(listingService.search(cardVariantId, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/listings/{listingId}")
    public ApiResponse<ListingView.Detail> detail(@PathVariable UUID listingId) {
        return ApiResponse.success(listingService.detail(listingId));
    }

    @GetMapping("/users/me/listings")
    public ApiResponse<ListingView.Page<ListingView.Summary>> mine(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(listingService.mine(userId, page, size));
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ListingView.Detail> create(
            @CurrentUserId UUID userId,
            @Valid @RequestBody CreateListingRequest request
    ) {
        return ApiResponse.success(listingService.create(
                userId, request.cardVariantId(), request.cardCondition(), request.description(),
                request.price(), request.shippingFee(), request.uploadSessionIds()
        ));
    }

    @PatchMapping("/listings/{listingId}")
    public ApiResponse<ListingView.Detail> update(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId,
            @Valid @RequestBody UpdateListingRequest request
    ) {
        return ApiResponse.success(listingService.update(
                userId, listingId, request.cardCondition(), request.description(), request.price(), request.shippingFee()
        ));
    }

    @PostMapping("/listings/{listingId}/hide")
    public ApiResponse<ListingView.Detail> hide(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(listingService.hide(userId, listingId));
    }

    @PostMapping("/listings/{listingId}/activate")
    public ApiResponse<ListingView.Detail> activate(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(listingService.activate(userId, listingId));
    }

    @DeleteMapping("/listings/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID listingId) {
        listingService.delete(userId, listingId);
    }

    public record CreateListingRequest(
            @NotNull UUID cardVariantId,
            @NotNull CardCondition cardCondition,
            @Size(max = 2_000) String description,
            @Min(500) @Max(100_000_000) long price,
            @Min(0) long shippingFee,
            @NotNull @Size(min = 2, max = 6) List<@NotNull UUID> uploadSessionIds
    ) {}

    public record UpdateListingRequest(
            @NotNull CardCondition cardCondition,
            @Size(max = 2_000) String description,
            @Min(500) @Max(100_000_000) long price,
            @Min(0) long shippingFee
    ) {}
}
