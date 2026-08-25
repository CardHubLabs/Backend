package com.kant.cardhub.cart.infrastructure;

import com.kant.cardhub.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    boolean existsByUserIdAndListingId(UUID userId, UUID listingId);
    Optional<CartItem> findByIdAndUserId(UUID id, UUID userId);
    List<CartItem> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteAllByListingId(UUID listingId);
}
