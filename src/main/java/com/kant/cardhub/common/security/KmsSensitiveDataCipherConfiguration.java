package com.kant.cardhub.common.security;

import com.kant.cardhub.common.config.SecurityDataProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration
@ConditionalOnProperty(name = "cardhub.security.cipher-provider", havingValue = "kms")
public class KmsSensitiveDataCipherConfiguration {

    @Bean(destroyMethod = "close")
    KmsClient kmsClient(SecurityDataProperties properties) {
        requireKmsConfiguration(properties);
        return KmsClient.builder()
                .region(Region.of(properties.kmsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    SensitiveDataCipher kmsSensitiveDataCipher(
            KmsClient kmsClient,
            SecurityDataProperties properties
    ) {
        requireKmsConfiguration(properties);
        return new KmsSensitiveDataCipher(kmsClient, properties.kmsKeyId());
    }

    private void requireKmsConfiguration(SecurityDataProperties properties) {
        if (properties.kmsKeyId() == null || properties.kmsKeyId().isBlank()) {
            throw new IllegalStateException("KMS_KEY_ID is required when CIPHER_PROVIDER=kms");
        }
        if (properties.kmsRegion() == null || properties.kmsRegion().isBlank()) {
            throw new IllegalStateException("KMS_REGION or AWS_REGION is required when CIPHER_PROVIDER=kms");
        }
    }
}
