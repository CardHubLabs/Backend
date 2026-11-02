package com.kant.cardhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cardhub.security")
public record SecurityDataProperties(
        String cipherProvider,
        String piiKey,
        String piiKeyVersion,
        String kmsKeyId,
        String kmsRegion
) {
}
