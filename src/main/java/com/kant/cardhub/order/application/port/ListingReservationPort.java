package com.kant.cardhub.order.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListingReservationPort {

    List<ListingSnapshot> lockAvailable(UUID buyerId, List<UUID> listingIds);

    void reserveAll(List<UUID> listingIds, UUID orderId, Instant expiresAt);

    void releaseOwned(List<UUID> listingIds, UUID orderId);

    void markSold(List<UUID> listingIds, UUID orderId);

    void reactivateSold(List<UUID> listingIds);
}
