package com.kant.cardhub.alert.application;

import java.util.OptionalLong;
import java.util.UUID;

/** Read-only boundary to the listing module. */
public interface ListingPriceReader {

    OptionalLong currentActivePrice(UUID listingId);
}

