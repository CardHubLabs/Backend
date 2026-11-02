package com.kant.cardhub.cart.application;

import com.kant.cardhub.listing.domain.ListingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartView(List<SellerGroup> sellerGroups, List<Item> unavailableItems, int itemCount) {
    public record SellerGroup(
            UUID sellerId,
            List<Item> items,
            long itemsTotal,
            long shippingFee,
            long orderTotal
    ) {}

    public record Item(
            UUID cartItemId,
            UUID listingId,
            UUID sellerId,
            UUID cardVariantId,
            long price,
            long shippingFee,
            ListingStatus listingStatus,
            boolean available,
            Instant addedAt
    ) {}
}
