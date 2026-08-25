package com.kant.cardhub.user.api;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateMeRequest(
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.")
        String nickname,
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,
        Boolean termsAccepted
) {
}
