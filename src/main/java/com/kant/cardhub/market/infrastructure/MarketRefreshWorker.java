package com.kant.cardhub.market.infrastructure;

import com.kant.cardhub.market.application.MarketApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "cardhub.worker.enabled", havingValue = "true")
public class MarketRefreshWorker {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshWorker.class);

    private final MarketApplicationService marketService;

    public MarketRefreshWorker(MarketApplicationService marketService) {
        this.marketService = marketService;
    }

    @Scheduled(
            initialDelayString = "${cardhub.worker.market-refresh-initial-delay:60000}",
            fixedDelayString = "${cardhub.worker.market-refresh-delay:3600000}"
    )
    public void refreshHourly() {
        for (UUID variantId : marketService.findRefreshCandidates()) {
            try {
                marketService.refresh(variantId);
            } catch (RuntimeException failure) {
                log.warn("Market statistic refresh failed variantId={}", variantId, failure);
            }
        }
    }
}
