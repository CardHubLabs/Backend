package com.kant.cardhub.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "market_price_stats")
public class MarketPriceStat {

    @Id
    @Column(name = "card_variant_id")
    private UUID cardVariantId;

    @Column(name = "recent_price")
    private Long recentPrice;

    @Column(name = "average_7d")
    private Long average7d;

    @Column(name = "median_30d")
    private Long median30d;

    @Column(name = "active_lowest_price")
    private Long activeLowestPrice;

    @Column(name = "volume_30d", nullable = false)
    private long volume30d;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    protected MarketPriceStat() {
    }

    public static MarketPriceStat create(UUID cardVariantId) {
        MarketPriceStat stat = new MarketPriceStat();
        stat.cardVariantId = cardVariantId;
        stat.volume30d = 0;
        stat.calculatedAt = Instant.EPOCH;
        return stat;
    }

    public void refresh(Long recent, Long average7d, Long median30d, Long activeLowest, long volume30d, Instant now) {
        this.recentPrice = recent;
        this.average7d = average7d;
        this.median30d = median30d;
        this.activeLowestPrice = activeLowest;
        this.volume30d = volume30d;
        this.calculatedAt = now;
    }

    public UUID getCardVariantId() { return cardVariantId; }
    public Long getRecentPrice() { return recentPrice; }
    public Long getAverage7d() { return average7d; }
    public Long getMedian30d() { return median30d; }
    public Long getActiveLowestPrice() { return activeLowestPrice; }
    public long getVolume30d() { return volume30d; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
