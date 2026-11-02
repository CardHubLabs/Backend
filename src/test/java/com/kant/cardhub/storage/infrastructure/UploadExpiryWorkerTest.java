package com.kant.cardhub.storage.infrastructure;

import com.kant.cardhub.storage.application.UploadService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadExpiryWorkerTest {

    @Test
    void workerDrainsFullBatchesAndStopsAtFirstPartialBatch() {
        UploadService uploadService = mock(UploadService.class);
        when(uploadService.expirePending(200)).thenReturn(200, 200, 3);
        UploadExpiryWorker worker = new UploadExpiryWorker(uploadService);

        worker.expirePendingUploads();

        verify(uploadService, times(3)).expirePending(200);
    }
}
