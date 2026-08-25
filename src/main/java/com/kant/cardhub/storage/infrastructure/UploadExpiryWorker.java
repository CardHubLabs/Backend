package com.kant.cardhub.storage.infrastructure;

import com.kant.cardhub.storage.application.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cardhub.worker.enabled", havingValue = "true")
public class UploadExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(UploadExpiryWorker.class);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final UploadService uploadService;

    public UploadExpiryWorker(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Scheduled(fixedDelayString = "${cardhub.upload.expiry-poll-delay:60000}")
    public void expirePendingUploads() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int expired = uploadService.expirePending(BATCH_SIZE);
            total += expired;
            if (expired < BATCH_SIZE) break;
        }
        if (total > 0) log.info("Expired {} pending upload sessions", total);
    }
}
