package com.kant.cardhub.cart.application;

import com.kant.cardhub.cart.domain.CartItem;
import com.kant.cardhub.cart.infrastructure.CartItemRepository;
import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock
    private CartItemRepository cartRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ActiveUserLock activeUserLock;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(
                cartRepository, listingRepository, Clock.fixed(NOW, ZoneOffset.UTC), activeUserLock);
    }

    @Test
    void cartIsGroupedBySellerAndChargesHighestShippingFeeOnce() {
        UUID userId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        Listing first = listing(sellerA, 10_000, 3_000);
        Listing second = listing(sellerA, 20_000, 5_000);
        Listing third = listing(sellerB, 7_000, 2_000);
        List<CartItem> cartItems = List.of(
                CartItem.create(userId, first.getId(), NOW),
                CartItem.create(userId, second.getId(), NOW.plusSeconds(1)),
                CartItem.create(userId, third.getId(), NOW.plusSeconds(2))
        );
        when(cartRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(cartItems);
        when(listingRepository.findAllById(List.of(first.getId(), second.getId(), third.getId())))
                .thenReturn(List.of(first, second, third));

        CartView result = service.get(userId);

        assertThat(result.itemCount()).isEqualTo(3);
        assertThat(result.sellerGroups()).hasSize(2);
        CartView.SellerGroup sellerAGroup = result.sellerGroups().stream()
                .filter(group -> group.sellerId().equals(sellerA))
                .findFirst().orElseThrow();
        assertThat(sellerAGroup.itemsTotal()).isEqualTo(30_000);
        assertThat(sellerAGroup.shippingFee()).isEqualTo(5_000);
        assertThat(sellerAGroup.orderTotal()).isEqualTo(35_000);
    }

    private static Listing listing(UUID sellerId, long price, long shippingFee) {
        return Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT, null, price, shippingFee, NOW
        );
    }
}
