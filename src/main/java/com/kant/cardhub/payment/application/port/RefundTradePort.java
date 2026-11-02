package com.kant.cardhub.payment.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RefundTradePort {
    void excludeOrder(UUID orderId, String reason, Instant excludedAt);
}
