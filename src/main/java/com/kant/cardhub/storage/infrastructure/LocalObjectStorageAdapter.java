package com.kant.cardhub.storage.infrastructure;

import com.kant.cardhub.storage.application.ObjectStoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!prod")
@ConditionalOnProperty(name = "cardhub.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageAdapter implements ObjectStoragePort {

    private final Map<String, LocalObject> objects = new ConcurrentHashMap<>();

    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType, long fileSize, Instant expiresAt) {
        objects.put(storageKey, new LocalObject(contentType, fileSize, null));
        return new PresignedUpload(
                "/api/v1/uploads/local?key=" + encode(storageKey),
                expiresAt
        );
    }

    @Override
    public StoredObjectMetadata head(String storageKey) {
        LocalObject object = requireObject(storageKey);
        if (object.bytes() == null) throw new IllegalStateException("Object upload is not complete: " + storageKey);
        return new StoredObjectMetadata(
                object.contentType(), object.fileSize(), Arrays.copyOf(object.bytes(), Math.min(12, object.bytes().length))
        );
    }

    @Override
    public String publicUrl(String storageKey) {
        return "/api/v1/listings/assets?key=" + encode(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        objects.remove(storageKey);
    }

    public void put(String storageKey, String contentType, byte[] bytes) {
        LocalObject expected = requireObject(storageKey);
        if (!expected.contentType().equalsIgnoreCase(contentType) || expected.fileSize() != bytes.length) {
            throw new IllegalArgumentException("Uploaded object metadata does not match presigned request");
        }
        objects.put(storageKey, new LocalObject(
                expected.contentType(), expected.fileSize(), Arrays.copyOf(bytes, bytes.length)
        ));
    }

    public LocalStoredObject get(String storageKey) {
        LocalObject object = requireObject(storageKey);
        if (object.bytes() == null) throw new IllegalStateException("Object upload is not complete: " + storageKey);
        return new LocalStoredObject(object.contentType(), Arrays.copyOf(object.bytes(), object.bytes().length));
    }

    private LocalObject requireObject(String storageKey) {
        LocalObject object = objects.get(storageKey);
        if (object == null) throw new IllegalStateException("Object does not exist: " + storageKey);
        return object;
    }

    private static String encode(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private record LocalObject(String contentType, long fileSize, byte[] bytes) {}
    public record LocalStoredObject(String contentType, byte[] bytes) {}
}
