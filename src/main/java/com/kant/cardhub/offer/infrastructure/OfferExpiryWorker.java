package com.kant.cardhub.offer.infrastructure;

import com.kant.cardhub.offer.application.OfferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cardhub.worker.enabled", havingValue = "true")
public class OfferExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryWorker.class);
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final OfferService offerService;

    public OfferExpiryWorker(OfferService offerService) {
        this.offerService = offerService;
    }

    @Scheduled(fixedDelayString = "${cardhub.offer.expiry-poll-delay:60000}")
    public void expireDueOffers() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int expired = offerService.expireDueOffers(BATCH_SIZE);
            total += expired;
            if (expired < BATCH_SIZE) break;
        }
        if (total > 0) log.info("Expired {} offers", total);
    }
}
