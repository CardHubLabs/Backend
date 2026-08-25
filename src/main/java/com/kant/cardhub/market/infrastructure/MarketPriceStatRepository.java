package com.kant.cardhub.market.infrastructure;

import com.kant.cardhub.market.domain.MarketPriceStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MarketPriceStatRepository extends JpaRepository<MarketPriceStat, UUID> {

    @Query(value = """
            select candidate.card_variant_id
              from (
                    select card_variant_id from listings where status = 'ACTIVE'
                    union
                    select card_variant_id from trades where excluded = false
                    union
                    select card_variant_id from market_price_stats
                   ) candidate
             order by candidate.card_variant_id
            """, nativeQuery = true)
    List<UUID> findRefreshCandidateIds();
}
