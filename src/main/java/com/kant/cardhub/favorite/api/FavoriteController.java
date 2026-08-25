package com.kant.cardhub.favorite.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.favorite.application.FavoriteService;
import com.kant.cardhub.favorite.domain.Favorite;
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
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{listingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FavoriteResponse> add(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        return ApiResponse.success(FavoriteResponse.from(favoriteService.add(userId, listingId)));
    }

    @DeleteMapping("/{listingId}")
    public ApiResponse<Void> remove(
            @CurrentUserId UUID userId,
            @PathVariable UUID listingId
    ) {
        favoriteService.remove(userId, listingId);
        return ApiResponse.empty();
    }

    @GetMapping
    public ApiResponse<FavoritePageResponse> list(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Favorite> result = favoriteService.list(userId, page, size);
        return ApiResponse.success(FavoritePageResponse.from(result));
    }

    public record FavoriteResponse(UUID id, UUID listingId, Instant createdAt) {
        static FavoriteResponse from(Favorite favorite) {
            return new FavoriteResponse(favorite.getId(), favorite.getListingId(), favorite.getCreatedAt());
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
