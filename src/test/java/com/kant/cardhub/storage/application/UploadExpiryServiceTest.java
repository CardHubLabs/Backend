package com.kant.cardhub.storage.application;

import com.kant.cardhub.storage.domain.UploadSession;
import com.kant.cardhub.storage.domain.UploadStatus;
import com.kant.cardhub.storage.infrastructure.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock private UploadSessionRepository repository;
    @Mock private ObjectStoragePort objectStorage;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(
                repository, objectStorage, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void exactBoundaryExpiresOnceAndRepeatedRunIsIdempotent() {
        UploadSession boundary = UploadSession.create(
                UUID.randomUUID(), "listing-images/boundary.jpg", "boundary.jpg", "image/jpeg", 100,
                NOW, NOW.minusSeconds(900)
        );
        when(repository.lockExpiredPending(NOW, 200))
                .thenReturn(List.of(boundary))
                .thenReturn(List.of());

        int firstRun = uploadService.expirePending(200);
        int repeatedRun = uploadService.expirePending(200);

        assertThat(firstRun).isEqualTo(1);
        assertThat(repeatedRun).isZero();
        assertThat(boundary.getStatus()).isEqualTo(UploadStatus.EXPIRED);
        verify(objectStorage, times(1)).delete("listing-images/boundary.jpg");
        verify(repository, times(2)).lockExpiredPending(NOW, 200);
    }
}
