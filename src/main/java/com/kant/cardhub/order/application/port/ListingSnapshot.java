package com.kant.cardhub.order.application.port;

import java.util.UUID;

public record ListingSnapshot(
        UUID id,
        UUID sellerId,
        UUID cardVariantId,
        String title,
        long price,
        long shippingFee,
        String cardCondition,
        String primaryImageStorageKey
) {
    public ListingSnapshot(
            UUID id,
            UUID sellerId,
            UUID cardVariantId,
            String title,
            long price,
            long shippingFee
    ) {
        this(id, sellerId, cardVariantId, title, price, shippingFee, "MINT", null);
    }
}
