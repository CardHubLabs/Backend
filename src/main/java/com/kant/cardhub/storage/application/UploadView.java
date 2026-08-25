package com.kant.cardhub.storage.application;

import java.time.Instant;
import java.util.UUID;

public final class UploadView {
    private UploadView() {
    }

    public record Presigned(
            UUID uploadSessionId,
            String storageKey,
            String uploadUrl,
            Instant expiresAt
    ) {}

    public record Completed(UUID uploadSessionId, String storageKey, String publicUrl) {}

    public record Attachment(UUID uploadSessionId, String storageKey, String publicUrl) {}
}
