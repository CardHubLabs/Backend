package com.kant.cardhub.market.infrastructure;

import com.kant.cardhub.market.domain.MarketPriceStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarketPriceStatRepository extends JpaRepository<MarketPriceStat, UUID> {

    @Query(value = """
            select
                (array_agg(t.price order by t.occurred_at desc, t.id desc))[1]::bigint as "recentPrice",
                round(avg(t.price) filter (where t.occurred_at >= :since7d))::bigint as "average7d",
                round((percentile_cont(0.5) within group (order by t.price)
                    filter (where t.occurred_at >= :since7d))::numeric)::bigint as "median7d",
                round(avg(t.price) filter (where t.occurred_at >= :since30d))::bigint as "average30d",
                round((percentile_cont(0.5) within group (order by t.price)
                    filter (where t.occurred_at >= :since30d))::numeric)::bigint as "median30d",
                round(avg(t.price) filter (where t.occurred_at >= :since90d))::bigint as "average90d",
                round((percentile_cont(0.5) within group (order by t.price)
                    filter (where t.occurred_at >= :since90d))::numeric)::bigint as "median90d",
                count(*) filter (where t.occurred_at >= :since30d) as "volume30d"
              from trades t
             where t.card_variant_id = :variantId
               and t.excluded = false
            """, nativeQuery = true)
    MarketAggregate aggregate(
            @Param("variantId") UUID variantId,
            @Param("since7d") Instant since7d,
            @Param("since30d") Instant since30d,
            @Param("since90d") Instant since90d
    );

    @Query(value = """
            select min(price)
              from listings
             where card_variant_id = :variantId
               and status = 'ACTIVE'
               and moderation_status = 'CLEAR'
            """, nativeQuery = true)
    Long findActiveLowestPrice(@Param("variantId") UUID variantId);

    @Query(value = """
            select candidate.card_variant_id
              from (
                    select card_variant_id from listings
                     where status = 'ACTIVE' and moderation_status = 'CLEAR'
                    union
                    select card_variant_id from trades where excluded = false
                    union
                    select card_variant_id from market_price_stats
                   ) candidate
             order by candidate.card_variant_id
            """, nativeQuery = true)
    List<UUID> findRefreshCandidateIds();

    interface MarketAggregate {
        Long getRecentPrice();
        Long getAverage7d();
        Long getMedian7d();
        Long getAverage30d();
        Long getMedian30d();
        Long getAverage90d();
        Long getMedian90d();
        long getVolume30d();
    }
}
