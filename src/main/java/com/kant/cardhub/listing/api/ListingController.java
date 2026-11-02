package com.kant.cardhub.listing.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.listing.application.ListingService;
import com.kant.cardhub.listing.application.ListingView;
import com.kant.cardhub.listing.domain.CardCondition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Listings", description = "매물 검색, 상세와 판매 lifecycle 관리 API")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/listings")
    @SecurityRequirements
    @Operation(
            summary = "공개 매물 검색",
            description = "차단되지 않은 ACTIVE 매물을 검색합니다. effectiveStatus는 BLOCKED가 우선하고 "
                    + "그 외에는 lifecycle status와 동일하지만, 공개 검색에서는 ACTIVE만 반환됩니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "매물 검색 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "가격 범위, 정렬 또는 페이지 조건 오류")
            }
    )
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
    @SecurityRequirements
    @Operation(
            summary = "공개 매물 상세 조회",
            description = "공개 가능한 매물 상세를 조회합니다. 관리자에게 차단된 매물은 존재를 노출하지 않고 404를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "매물 상세 조회 성공", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물이 없거나 공개 대상이 아님")
            }
    )
    public ApiResponse<ListingView.Detail> detail(@PathVariable UUID listingId) {
        return ApiResponse.success(listingService.detail(listingId));
    }

    @GetMapping("/users/me/listings")
    @Operation(
            summary = "내 매물 목록 조회",
            description = "raw lifecycle status와 moderationStatus를 유지하면서 effectiveStatus를 함께 반환합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "내 매물 목록 조회 성공", useReturnTypeSchema = true)
    )
    public ApiResponse<ListingView.Page<ListingView.Summary>> mine(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(listingService.mine(userId, page, size));
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "매물 등록",
            description = "등록 가능한 카드 Variant와 검증 완료된 이미지 2~6개로 ACTIVE 매물을 생성합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "매물 등록 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "가격, 이미지 수 또는 요청 형식 오류"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "이미지 업로드 세션 소유권 위반"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "등록 비활성 Variant 또는 이미지 검증 실패")
            }
    )
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
    @Operation(
            summary = "내 매물 수정",
            description = "ACTIVE 또는 HIDDEN인 본인 매물만 수정할 수 있으며, 차단된 매물은 수정할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "매물 수정 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자가 아니거나 관리자 차단 상태"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "수정할 수 없는 lifecycle 상태")
            }
    )
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
    @Operation(
            summary = "내 매물 숨김",
            description = "판매자가 ACTIVE 매물을 HIDDEN으로 전환합니다. 관리자 차단 상태에서는 수행할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "매물 숨김 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자가 아니거나 관리자 차단 상태"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "숨길 수 없는 lifecycle 상태")
            }
    )
    public ApiResponse<ListingView.Detail> hide(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(listingService.hide(userId, listingId));
    }

    @PostMapping("/listings/{listingId}/activate")
    @Operation(
            summary = "내 매물 재활성화",
            description = "판매자가 HIDDEN 매물을 ACTIVE로 전환합니다. 관리자 차단 상태는 재활성화할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "매물 활성화 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자가 아니거나 관리자 차단 상태"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "활성화할 수 없는 lifecycle 상태")
            }
    )
    public ApiResponse<ListingView.Detail> activate(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(listingService.activate(userId, listingId));
    }

    @DeleteMapping("/listings/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "내 매물 삭제",
            description = "판매자가 판매 전 매물을 DELETED로 전환합니다. 차단되거나 예약·판매된 매물은 삭제할 수 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "매물 삭제 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "소유자가 아니거나 관리자 차단 상태"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "삭제할 수 없는 lifecycle 상태")
            }
    )
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID listingId) {
        listingService.delete(userId, listingId);
    }

    @Schema(description = "검증 완료 이미지로 매물을 등록하는 요청")
    public record CreateListingRequest(
            @Schema(description = "등록 가능한 카드 Variant ID", example = "6fd16860-e4b2-40a8-a239-b9af9bd6ff17")
            @NotNull UUID cardVariantId,
            @Schema(description = "카드 상태", example = "NEAR_MINT")
            @NotNull CardCondition cardCondition,
            @Schema(description = "매물 설명", example = "슬리브에 보관해 모서리 손상이 없습니다.")
            @Size(max = 2_000) String description,
            @Schema(description = "상품 가격(원)", example = "30000")
            @Min(500) @Max(100_000_000) long price,
            @Schema(description = "배송비(원)", example = "3000")
            @Min(0) long shippingFee,
            @Schema(
                    description = "검증 완료된 업로드 세션 ID 2~6개",
                    example = "[\"26d94825-aed5-4fa3-ac77-ad278ff384e1\",\"7686097f-42e5-4426-bf31-f46b0788405f\"]"
            )
            @NotNull @Size(min = 2, max = 6) List<@NotNull UUID> uploadSessionIds
    ) {}

    public record UpdateListingRequest(
            @NotNull CardCondition cardCondition,
            @Size(max = 2_000) String description,
            @Min(500) @Max(100_000_000) long price,
            @Min(0) long shippingFee
    ) {}
}
