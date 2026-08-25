package com.kant.cardhub.review.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.review.domain.Review;
import com.kant.cardhub.review.infrastructure.ReviewRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewApplicationService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;
    private final ActiveUserLock activeUserLock;

    public ReviewApplicationService(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            Clock clock,
            ActiveUserLock activeUserLock
    ) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
        this.activeUserLock = activeUserLock;
    }

    @Transactional
    public Review create(UUID buyerId, UUID orderId, int rating, List<String> tags, String content) {
        activeUserLock.requireActive(buyerId);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.requireBuyer(buyerId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed order can be reviewed");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new IllegalStateException("A review already exists for this order");
        }
        return reviewRepository.save(Review.create(
                orderId, buyerId, order.getSellerId(), rating, tags, content, clock.instant()));
    }

    @Transactional(readOnly = true)
    public SellerReviews forSeller(UUID sellerId, int page, int size) {
        List<Review> reviews = reviewRepository.findBySellerIdOrderByCreatedAtDesc(
                sellerId, PageRequest.of(page, Math.max(1, Math.min(size, 100))));
        Double average = reviewRepository.averageRating(sellerId);
        return new SellerReviews(reviews, average == null ? 0.0 : average, reviewRepository.countBySellerId(sellerId));
    }

    public record SellerReviews(List<Review> reviews, double averageRating, long totalCount) {
        public SellerReviews {
            reviews = List.copyOf(reviews);
        }
    }
}
