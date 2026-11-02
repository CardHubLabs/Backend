package com.kant.cardhub.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void completesOnboardingOnlyAfterBirthDateAndTermsAreStored() {
        User user = new User("member@example.com", "member");

        assertThat(user.onboardingCompleted()).isFalse();

        user.updateProfile(null, LocalDate.of(1990, 1, 1), Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(user.onboardingCompleted()).isTrue();
    }

    @Test
    void withdrawalAnonymizesPersonalDataAndPreservesIdentity() {
        User user = new User("member@example.com", "member");
        var id = user.getId();

        user.withdraw(Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).startsWith("withdrawn-");
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getBirthDate()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    }
}
