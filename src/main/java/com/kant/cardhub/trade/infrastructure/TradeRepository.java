package com.kant.cardhub.trade.infrastructure;

import com.kant.cardhub.trade.domain.Trade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {
    List<Trade> findByOrderId(UUID orderId);
    Optional<Trade> findByOrderItemId(UUID orderItemId);

    @Query("select t from Trade t where t.cardVariantId = :variantId and t.excluded = false order by t.occurredAt desc")
    List<Trade> findRecent(@Param("variantId") UUID variantId, Pageable pageable);

    @Query("select avg(t.price) from Trade t where t.cardVariantId = :variantId and t.excluded = false and t.occurredAt >= :since")
    Double averageSince(@Param("variantId") UUID variantId, @Param("since") Instant since);

    @Query("select count(t) from Trade t where t.cardVariantId = :variantId and t.excluded = false and t.occurredAt >= :since")
    long countSince(@Param("variantId") UUID variantId, @Param("since") Instant since);

    @Query(value = """
            select percentile_cont(0.5) within group (order by price)
              from trades
             where card_variant_id = :variantId
               and excluded = false
               and occurred_at >= :since
            """, nativeQuery = true)
    Number medianSince(@Param("variantId") UUID variantId, @Param("since") Instant since);
}
