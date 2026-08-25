package com.kant.cardhub.auth.domain;

public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static SocialProvider fromRegistrationId(String registrationId) {
        return valueOf(registrationId.toUpperCase());
    }
}
