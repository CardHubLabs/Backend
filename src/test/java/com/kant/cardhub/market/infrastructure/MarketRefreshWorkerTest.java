package com.kant.cardhub.market.infrastructure;

import com.kant.cardhub.market.application.MarketApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketRefreshWorkerTest {

    @Test
    void refreshesEveryCandidateAndIsolatesIndividualFailures() {
        MarketApplicationService marketService = mock(MarketApplicationService.class);
        UUID first = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        when(marketService.findRefreshCandidates()).thenReturn(List.of(first, failing, last));
        doThrow(new IllegalStateException("broken statistic"))
                .when(marketService).refresh(failing);

        new MarketRefreshWorker(marketService).refreshHourly();

        verify(marketService).refresh(first);
        verify(marketService).refresh(failing);
        verify(marketService).refresh(last);
    }
}
