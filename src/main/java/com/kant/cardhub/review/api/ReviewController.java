package com.kant.cardhub.review.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.common.security.CurrentUserId;
import com.kant.cardhub.review.application.ReviewApplicationService;
import com.kant.cardhub.review.domain.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewApplicationService reviewService;

    public ReviewController(ReviewApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/orders/{orderId}/review")
    public ApiResponse<ReviewResponse> create(
            @CurrentUserId UUID buyerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ApiResponse.success(ReviewResponse.from(reviewService.create(
                buyerId, orderId, request.rating(), request.tags(), request.content())));
    }

    @GetMapping("/sellers/{sellerId}/reviews")
    public ApiResponse<SellerReviewsResponse> sellerReviews(
            @PathVariable UUID sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReviewApplicationService.SellerReviews result = reviewService.forSeller(sellerId, page, size);
        return ApiResponse.success(new SellerReviewsResponse(
                result.reviews().stream().map(ReviewResponse::from).toList(),
                result.averageRating(), result.totalCount()));
    }

    public record CreateReviewRequest(
            @Min(1) @Max(5) int rating,
            @Size(max = 10) List<String> tags,
            @Size(max = 1000) String content
    ) {
    }

    public record ReviewResponse(
            UUID id,
            UUID orderId,
            UUID buyerId,
            UUID sellerId,
            int rating,
            List<String> tags,
            String content,
            Instant createdAt
    ) {
        static ReviewResponse from(Review review) {
            return new ReviewResponse(
                    review.getId(), review.getOrderId(), review.getBuyerId(), review.getSellerId(),
                    review.getRating(), review.getTags(), review.getContent(), review.getCreatedAt());
        }
    }

    public record SellerReviewsResponse(
            List<ReviewResponse> reviews,
            double averageRating,
            long totalCount
    ) {
    }
}
