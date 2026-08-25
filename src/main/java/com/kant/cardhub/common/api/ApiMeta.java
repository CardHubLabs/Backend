package com.kant.cardhub.common.api;

import java.time.Instant;

public record ApiMeta(Instant timestamp) {

    public static ApiMeta now() {
        return new ApiMeta(Instant.now());
    }
}
