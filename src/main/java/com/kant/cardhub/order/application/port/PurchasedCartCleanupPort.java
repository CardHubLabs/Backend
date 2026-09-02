package com.kant.cardhub.order.application.port;

import java.util.List;
import java.util.UUID;

public interface PurchasedCartCleanupPort {
    void removePurchasedListings(UUID buyerId, List<UUID> listingIds);
}
