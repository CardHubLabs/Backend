package com.kant.cardhub.auth.application;

import java.time.Instant;

public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken
) {
}
