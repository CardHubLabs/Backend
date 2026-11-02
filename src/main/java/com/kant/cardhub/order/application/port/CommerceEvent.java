package com.kant.cardhub.order.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CommerceEvent(
        CommerceEventType type,
        UUID recipientId,
        UUID orderId,
        String dedupeKey,
        Map<String, String> attributes,
        Instant occurredAt
) {
    public CommerceEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static CommerceEvent orderEvent(
            CommerceEventType type,
            UUID recipientId,
            UUID orderId,
            Instant occurredAt
    ) {
        return new CommerceEvent(
                type,
                recipientId,
                orderId,
                type + ":" + orderId + ":" + recipientId,
                Map.of(),
                occurredAt
        );
    }
}
