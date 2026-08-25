package com.kant.cardhub.market.application;

import com.kant.cardhub.catalog.infrastructure.CardVariantRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.market.domain.MarketPriceStat;
import com.kant.cardhub.market.infrastructure.MarketPriceStatRepository;
import com.kant.cardhub.trade.domain.Trade;
import com.kant.cardhub.trade.infrastructure.TradeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

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
        List<Trade> recent = tradeRepository.findRecent(variantId, PageRequest.of(0, 1));
        Double average = tradeRepository.averageSince(variantId, now.minus(Duration.ofDays(7)));
        Number median = tradeRepository.medianSince(variantId, now.minus(Duration.ofDays(30)));
        long volume = tradeRepository.countSince(variantId, now.minus(Duration.ofDays(30)));
        Number lowest = (Number) entityManager.createNativeQuery("""
                        select min(price) from listings
                         where card_variant_id = :variantId and status = 'ACTIVE'
                        """)
                .setParameter("variantId", variantId)
                .getSingleResult();

        MarketPriceStat stat = statRepository.findById(variantId)
                .orElseGet(() -> MarketPriceStat.create(variantId));
        stat.refresh(
                recent.isEmpty() ? null : recent.getFirst().getPrice(),
                average == null ? null : Math.round(average),
                median == null ? null : Math.round(median.doubleValue()),
                lowest == null ? null : lowest.longValue(),
                volume,
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
