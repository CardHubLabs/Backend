package com.kant.cardhub.cart.infrastructure;

import com.kant.cardhub.order.application.port.PurchasedCartCleanupPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CartOrderCleanupAdapter implements PurchasedCartCleanupPort {

    private final CartItemRepository cartItemRepository;

    public CartOrderCleanupAdapter(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    public void removePurchasedListings(UUID buyerId, List<UUID> listingIds) {
        cartItemRepository.deleteAllByUserIdAndListingIdIn(buyerId, listingIds);
    }
}
