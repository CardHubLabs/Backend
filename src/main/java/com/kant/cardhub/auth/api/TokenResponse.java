package com.kant.cardhub.auth.api;

import com.kant.cardhub.auth.application.AuthTokens;

import java.time.Instant;

public record TokenResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt
) {

    public static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse("Bearer", tokens.accessToken(), tokens.accessTokenExpiresAt());
    }
}
