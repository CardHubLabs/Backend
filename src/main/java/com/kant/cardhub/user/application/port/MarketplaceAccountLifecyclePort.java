package com.kant.cardhub.user.application.port;

import java.time.Instant;
import java.util.UUID;

public interface MarketplaceAccountLifecyclePort {

    void suspend(UUID userId, Instant now);

    void withdraw(UUID userId, Instant now);
}
