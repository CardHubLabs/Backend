package com.kant.cardhub.storage.domain;

import com.kant.cardhub.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSessionTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void uploadMustMatchDeclaredMetadataBeforeAttachment() {
        UUID ownerId = UUID.randomUUID();
        UploadSession session = UploadSession.create(
                ownerId, "listing-images/key.webp", "card.webp", "image/webp", 100,
                NOW.plus(15, ChronoUnit.MINUTES), NOW
        );

        assertThatThrownBy(() -> session.verify("image/png", 100, NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);

        session.verify("image/webp", 100, NOW.plusSeconds(2));
        session.attach(ownerId, NOW.plusSeconds(3));

        assertThat(session.getStatus()).isEqualTo(UploadStatus.ATTACHED);
        assertThat(session.getVerifiedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void anotherUserCannotAttachUpload() {
        UUID ownerId = UUID.randomUUID();
        UploadSession session = UploadSession.create(
                ownerId, "listing-images/key.jpg", "card.jpg", "image/jpeg", 100,
                NOW.plus(15, ChronoUnit.MINUTES), NOW
        );
        session.verify("image/jpeg", 100, NOW.plusSeconds(1));

        assertThatThrownBy(() -> session.attach(UUID.randomUUID(), NOW.plusSeconds(2)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verificationAtExactExpiryBoundaryIsRejectedAndExpiresSession() {
        UUID ownerId = UUID.randomUUID();
        Instant expiresAt = NOW.plus(15, ChronoUnit.MINUTES);
        UploadSession session = UploadSession.create(
                ownerId, "listing-images/key.png", "card.png", "image/png", 100,
                expiresAt, NOW
        );

        assertThatThrownBy(() -> session.verify("image/png", 100, expiresAt))
                .isInstanceOf(BusinessException.class);
        assertThat(session.getStatus()).isEqualTo(UploadStatus.EXPIRED);
    }
}
