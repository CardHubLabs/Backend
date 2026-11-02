package com.kant.cardhub.listing.application;

import java.util.UUID;

public interface ListingEventPort {
    void onPublished(UUID listingId, UUID cardVariantId, UUID sellerId, long price);

    void onPriceChanged(
            UUID listingId,
            UUID cardVariantId,
            UUID sellerId,
            long previousPrice,
            long currentPrice
    );
}
