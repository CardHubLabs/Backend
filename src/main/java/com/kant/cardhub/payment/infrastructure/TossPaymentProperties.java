package com.kant.cardhub.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cardhub.payment")
public record TossPaymentProperties(
        String provider,
        String baseUrl,
        String secretKey,
        java.time.Duration connectTimeout,
        java.time.Duration readTimeout
) {
}
