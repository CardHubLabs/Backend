package com.kant.cardhub.order.application.port;

import java.util.UUID;

public interface AcceptedOfferPort {
    long consume(UUID offerId, UUID buyerId, UUID listingId);
}
