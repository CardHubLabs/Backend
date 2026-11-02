package com.kant.cardhub.storage.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "cardhub.storage.provider", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        validate(properties);
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties properties) {
        validate(properties);
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private void validate(StorageProperties properties) {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException("S3_BUCKET is required when STORAGE_PROVIDER=s3");
        }
        if (properties.region() == null || properties.region().isBlank()) {
            throw new IllegalStateException("AWS_REGION is required when STORAGE_PROVIDER=s3");
        }
        if (properties.cloudfrontBaseUrl() == null || properties.cloudfrontBaseUrl().isBlank()) {
            throw new IllegalStateException("CLOUDFRONT_BASE_URL is required when STORAGE_PROVIDER=s3");
        }
    }
}
