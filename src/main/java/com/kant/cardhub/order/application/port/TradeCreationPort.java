package com.kant.cardhub.order.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeCreationPort {

    List<UUID> createTrades(ConfirmedOrder order, Instant occurredAt);

    record ConfirmedOrder(
            UUID orderId,
            UUID buyerId,
            UUID sellerId,
            List<ConfirmedItem> items
    ) {
        public ConfirmedOrder {
            items = List.copyOf(items);
        }
    }

    record ConfirmedItem(
            UUID orderItemId,
            UUID listingId,
            UUID cardVariantId,
            long price
    ) {
    }
}
