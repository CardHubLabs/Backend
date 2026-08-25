package com.kant.cardhub.cart.application;

import com.kant.cardhub.cart.domain.CartItem;
import com.kant.cardhub.cart.infrastructure.CartItemRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingStatus;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartRepository;
    private final ListingRepository listingRepository;
    private final Clock clock;
    private final ActiveUserLock activeUserLock;

    public CartService(
            CartItemRepository cartRepository,
            ListingRepository listingRepository,
            Clock clock,
            ActiveUserLock activeUserLock
    ) {
        this.cartRepository = cartRepository;
        this.listingRepository = listingRepository;
        this.clock = clock;
        this.activeUserLock = activeUserLock;
    }

    @Transactional
    public CartView.Item add(UUID userId, UUID listingId) {
        activeUserLock.requireActive(userId);
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LISTING_INVALID_STATE);
        }
        if (listing.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "본인의 매물은 장바구니에 담을 수 없습니다.");
        }
        if (cartRepository.existsByUserIdAndListingId(userId, listingId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_ALREADY_EXISTS);
        }
        try {
            CartItem item = cartRepository.saveAndFlush(CartItem.create(userId, listingId, clock.instant()));
            return toItem(item, listing);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CART_ITEM_ALREADY_EXISTS);
        }
    }

    public CartView get(UUID userId) {
        List<CartItem> cartItems = cartRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, Listing> listings = new HashMap<>();
        listingRepository.findAllById(cartItems.stream().map(CartItem::getListingId).toList())
                .forEach(listing -> listings.put(listing.getId(), listing));
        List<CartView.Item> items = cartItems.stream()
                .map(item -> toItem(item, listings.get(item.getListingId())))
                .toList();

        Map<UUID, List<CartView.Item>> bySeller = new LinkedHashMap<>();
        List<CartView.Item> unavailable = new ArrayList<>();
        for (CartView.Item item : items) {
            if (item.sellerId() == null) {
                unavailable.add(item);
            } else {
                bySeller.computeIfAbsent(item.sellerId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        List<CartView.SellerGroup> groups = bySeller.entrySet().stream().map(entry -> {
            List<CartView.Item> available = entry.getValue().stream().filter(CartView.Item::available).toList();
            long itemsTotal = available.stream().mapToLong(CartView.Item::price).sum();
            long shippingFee = available.stream().mapToLong(CartView.Item::shippingFee).max().orElse(0);
            return new CartView.SellerGroup(
                    entry.getKey(), List.copyOf(entry.getValue()), itemsTotal, shippingFee,
                    Math.addExact(itemsTotal, shippingFee)
            );
        }).toList();
        return new CartView(groups, List.copyOf(unavailable), items.size());
    }

    @Transactional
    public void remove(UUID userId, UUID cartItemId) {
        CartItem item = cartRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
        cartRepository.delete(item);
    }

    private static CartView.Item toItem(CartItem item, Listing listing) {
        if (listing == null) {
            return new CartView.Item(
                    item.getId(), item.getListingId(), null, null, 0, 0, ListingStatus.DELETED, false,
                    item.getCreatedAt()
            );
        }
        return new CartView.Item(
                item.getId(), listing.getId(), listing.getSellerId(), listing.getCardVariantId(),
                listing.getPrice(), listing.getShippingFee(), listing.getStatus(),
                listing.getStatus() == ListingStatus.ACTIVE, item.getCreatedAt()
        );
    }
}
