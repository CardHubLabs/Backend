package com.kant.cardhub.review.infrastructure;

import com.kant.cardhub.review.domain.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByOrderId(UUID orderId);
    Optional<Review> findByOrderId(UUID orderId);
    List<Review> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.sellerId = :sellerId")
    Double averageRating(@Param("sellerId") UUID sellerId);

    long countBySellerId(UUID sellerId);
}
