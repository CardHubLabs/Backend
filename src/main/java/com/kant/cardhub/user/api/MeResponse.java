package com.kant.cardhub.user.api;

import com.kant.cardhub.user.domain.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String nickname,
        LocalDate birthDate,
        String role,
        String status,
        boolean onboardingCompleted,
        Instant createdAt
) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getRole().name(),
                user.getStatus().name(),
                user.onboardingCompleted(),
                user.getCreatedAt()
        );
    }
}
