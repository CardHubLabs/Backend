package com.kant.cardhub.storage.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStorageAdapterTest {

    @Test
    void objectIsVisibleOnlyAfterMatchingPut() {
        LocalObjectStorageAdapter adapter = new LocalObjectStorageAdapter();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        var presigned = adapter.presignUpload(
                "listing-images/user/image.jpg", "image/jpeg", jpeg.length, Instant.parse("2026-08-25T01:00:00Z")
        );

        assertThat(presigned.uploadUrl()).contains("/api/v1/uploads/local?key=");
        assertThatThrownBy(() -> adapter.head("listing-images/user/image.jpg"))
                .isInstanceOf(IllegalStateException.class);

        adapter.put("listing-images/user/image.jpg", "image/jpeg", jpeg);

        assertThat(adapter.head("listing-images/user/image.jpg").signaturePrefix()).containsExactly(jpeg);
        assertThat(adapter.get("listing-images/user/image.jpg").bytes()).containsExactly(jpeg);
    }

    @Test
    void putRejectsMetadataThatDiffersFromPresignedRequest() {
        LocalObjectStorageAdapter adapter = new LocalObjectStorageAdapter();
        adapter.presignUpload("key", "image/png", 8, Instant.parse("2026-08-25T01:00:00Z"));

        assertThatThrownBy(() -> adapter.put("key", "image/jpeg", new byte[8]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.put("key", "image/png", new byte[7]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
