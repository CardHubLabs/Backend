package com.kant.cardhub.storage.infrastructure;

import com.kant.cardhub.storage.application.ObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "cardhub.storage.provider", havingValue = "s3")
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private static final int SIGNATURE_PREFIX_SIZE = 12;

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    private final Clock clock;

    public S3ObjectStorageAdapter(
            S3Client s3Client,
            S3Presigner presigner,
            StorageProperties properties,
            Clock clock
    ) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PresignedUpload presignUpload(
            String storageKey,
            String contentType,
            long fileSize,
            Instant expiresAt
    ) {
        Duration duration = Duration.between(clock.instant(), expiresAt);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Upload expiration must be in the future");
        }
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .contentType(contentType)
                .contentLength(fileSize)
                .build();
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(objectRequest)
                .build();
        String url = presigner.presignPutObject(request).url().toExternalForm();
        return new PresignedUpload(url, expiresAt);
    }

    @Override
    public StoredObjectMetadata head(String storageKey) {
        HeadObjectResponse metadata = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build());
        ResponseBytes<GetObjectResponse> prefix = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .range("bytes=0-" + (SIGNATURE_PREFIX_SIZE - 1))
                .build());
        return new StoredObjectMetadata(
                metadata.contentType(),
                metadata.contentLength(),
                prefix.asByteArray()
        );
    }

    @Override
    public String publicUrl(String storageKey) {
        String base = properties.cloudfrontBaseUrl().replaceAll("/+$", "");
        return base + "/" + storageKey;
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build());
    }
}
