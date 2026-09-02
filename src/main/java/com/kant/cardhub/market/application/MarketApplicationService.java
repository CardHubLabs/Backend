package com.kant.cardhub.market.application;

import com.kant.cardhub.catalog.infrastructure.CardVariantRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.market.domain.MarketPriceStat;
import com.kant.cardhub.market.infrastructure.MarketPriceStatRepository;
import com.kant.cardhub.trade.domain.Trade;
import com.kant.cardhub.trade.infrastructure.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarketApplicationService {

    private final TradeRepository tradeRepository;
    private final MarketPriceStatRepository statRepository;
    private final CardVariantRepository cardVariantRepository;
    private final Clock clock;

    public MarketApplicationService(
            TradeRepository tradeRepository,
            MarketPriceStatRepository statRepository,
            CardVariantRepository cardVariantRepository,
            Clock clock
    ) {
        this.tradeRepository = tradeRepository;
        this.statRepository = statRepository;
        this.cardVariantRepository = cardVariantRepository;
        this.clock = clock;
    }

    @Transactional
    public MarketPriceStat refresh(UUID variantId) {
        Instant now = clock.instant();
        MarketPriceStatRepository.MarketAggregate aggregate = statRepository.aggregate(
                variantId,
                now.minus(Duration.ofDays(7)),
                now.minus(Duration.ofDays(30)),
                now.minus(Duration.ofDays(90))
        );
        Long lowest = statRepository.findActiveLowestPrice(variantId);

        MarketPriceStat stat = statRepository.findById(variantId)
                .orElseGet(() -> MarketPriceStat.create(variantId));
        stat.refresh(
                aggregate.getRecentPrice(),
                aggregate.getAverage7d(),
                aggregate.getMedian7d(),
                aggregate.getAverage30d(),
                aggregate.getMedian30d(),
                aggregate.getAverage90d(),
                aggregate.getMedian90d(),
                lowest,
                aggregate.getVolume30d(),
                now
        );
        return statRepository.save(stat);
    }

    @Transactional(readOnly = true)
    public MarketPriceStat get(UUID variantId) {
        if (!cardVariantRepository.existsById(variantId)) {
            throw new BusinessException(ErrorCode.CATALOG_NOT_FOUND);
        }
        return statRepository.findById(variantId).orElseGet(() -> MarketPriceStat.create(variantId));
    }

    @Transactional(readOnly = true)
    public List<Trade> recentTrades(UUID variantId, int limit) {
        return tradeRepository.findRecent(variantId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    @Transactional(readOnly = true)
    public List<UUID> findRefreshCandidates() {
        return List.copyOf(statRepository.findRefreshCandidateIds());
    }
}
