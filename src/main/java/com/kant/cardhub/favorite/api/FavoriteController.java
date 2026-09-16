package com.kant.cardhub.favorite.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.favorite.application.FavoriteService;
import com.kant.cardhub.favorite.domain.Favorite;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@Tag(name = "Favorites", description = "카드 Variant 관심 등록과 조회 API")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{listingId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Deprecated(since = "v1", forRemoval = false)
    @Operation(
            summary = "매물 기준 관심 등록 (legacy)",
            description = "호환용 API입니다. 매물이 연결된 카드 Variant를 관심 대상으로 등록합니다. "
                    + "신규 클라이언트는 POST /favorites/card-variants/{variantId}를 사용해야 합니다.",
            deprecated = true,
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "관심 등록 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "매물을 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 관심 Variant")
            }
    )
    public ApiResponse<FavoriteResponse> add(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(FavoriteResponse.from(favoriteService.add(userId, listingId)));
    }

    @PostMapping("/card-variants/{variantId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "카드 Variant 관심 등록",
            description = "사용자의 관심 목록에 카드 Variant를 한 번 등록합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "관심 등록 완료", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Variant를 찾을 수 없음"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 관심 Variant")
            }
    )
    public ApiResponse<FavoriteResponse> addVariant(
            @CurrentUserId UUID userId,
            @PathVariable UUID variantId
    ) {
        return ApiResponse.success(FavoriteResponse.from(favoriteService.addVariant(userId, variantId)));
    }

    @DeleteMapping("/{listingId}")
    @Deprecated(since = "v1", forRemoval = false)
    @Operation(
            summary = "매물 기준 관심 해제 (legacy)",
            description = "호환용 API입니다. 신규 클라이언트는 "
                    + "DELETE /favorites/card-variants/{variantId}를 사용해야 합니다.",
            deprecated = true,
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관심 해제 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관심 항목 또는 매물을 찾을 수 없음")
            }
    )
    public ApiResponse<Void> remove(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        favoriteService.remove(userId, listingId);
        return ApiResponse.empty();
    }

    @DeleteMapping("/card-variants/{variantId}")
    @Operation(
            summary = "카드 Variant 관심 해제",
            description = "사용자의 관심 목록에서 지정한 카드 Variant를 제거합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "관심 해제 완료"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관심 항목을 찾을 수 없음")
            }
    )
    public ApiResponse<Void> removeVariant(
            @CurrentUserId UUID userId,
            @PathVariable UUID variantId
    ) {
        favoriteService.removeVariant(userId, variantId);
        return ApiResponse.empty();
    }

    @GetMapping
    @Operation(
            summary = "관심 Variant 목록 조회",
            description = "현재 사용자의 관심 Variant를 최신 등록순으로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "관심 목록 조회 성공", useReturnTypeSchema = true)
    )
    public ApiResponse<FavoritePageResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Favorite> result = favoriteService.list(userId, page, size);
        return ApiResponse.success(FavoritePageResponse.from(result));
    }

    public record FavoriteResponse(
            @Schema(description = "관심 항목 ID", example = "fbb58df3-f3a7-4ea1-bbfb-0bcc5222c503") UUID id,
            @Schema(description = "관심 대상 카드 Variant ID", example = "6fd16860-e4b2-40a8-a239-b9af9bd6ff17") UUID cardVariantId,
            @Schema(
                    description = "호환용 원본 매물 ID. 신규 응답에서는 cardVariantId를 사용합니다.",
                    deprecated = true,
                    nullable = true,
                    example = "786d3e18-ab50-4573-acd4-012356c4fe04"
            ) UUID listingId,
            @Schema(description = "관심 등록 시각(UTC)", example = "2026-09-02T03:15:00Z") Instant createdAt
    ) {
        static FavoriteResponse from(Favorite favorite) {
            return new FavoriteResponse(
                    favorite.getId(), favorite.getCardVariantId(),
                    favorite.getListingId(), favorite.getCreatedAt());
        }
    }

    public record FavoritePageResponse(
            List<FavoriteResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        static FavoritePageResponse from(Page<Favorite> result) {
            return new FavoritePageResponse(
                    result.getContent().stream().map(FavoriteResponse::from).toList(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages()
            );
        }
    }
}
