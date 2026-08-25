package com.kant.cardhub.order.domain;

import java.util.UUID;

public record OrderItemDraft(
        UUID listingId,
        UUID cardVariantId,
        String listingTitle,
        long price,
        long shippingFee
) {
    public OrderItemDraft {
        if (listingId == null || cardVariantId == null) {
            throw new IllegalArgumentException("listingId and cardVariantId are required");
        }
        if (price < 0 || shippingFee < 0) {
            throw new IllegalArgumentException("price and shippingFee must not be negative");
        }
        listingTitle = listingTitle == null ? "" : listingTitle;
    }
}
