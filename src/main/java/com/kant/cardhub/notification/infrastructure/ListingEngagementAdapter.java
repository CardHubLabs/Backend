package com.kant.cardhub.notification.infrastructure;

import com.kant.cardhub.listing.application.ListingEventPort;
import com.kant.cardhub.notification.application.ListingEngagementService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ListingEngagementAdapter implements ListingEventPort {

    private final ListingEngagementService listingEngagementService;

    public ListingEngagementAdapter(ListingEngagementService listingEngagementService) {
        this.listingEngagementService = listingEngagementService;
    }

    @Override
    public void onPublished(UUID listingId, UUID cardVariantId, UUID sellerId, long price) {
        listingEngagementService.onPublished(listingId, cardVariantId, sellerId, price);
    }

    @Override
    public void onPriceChanged(
            UUID listingId,
            UUID cardVariantId,
            UUID sellerId,
            long previousPrice,
            long currentPrice
    ) {
        listingEngagementService.onPriceChanged(
                listingId, cardVariantId, sellerId, previousPrice, currentPrice
        );
    }
}

