package com.kant.cardhub.storage.application;

import java.time.Instant;

public interface ObjectStoragePort {

    PresignedUpload presignUpload(String storageKey, String contentType, long fileSize, Instant expiresAt);

    StoredObjectMetadata head(String storageKey);

    String publicUrl(String storageKey);

    void delete(String storageKey);

    record PresignedUpload(String uploadUrl, Instant expiresAt) {}
    record StoredObjectMetadata(String contentType, long fileSize, byte[] signaturePrefix) {}
}
