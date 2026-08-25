package com.kant.cardhub.storage.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cardhub.storage")
public record StorageProperties(
        String provider,
        String bucket,
        String region,
        String cloudfrontBaseUrl
) {
}
