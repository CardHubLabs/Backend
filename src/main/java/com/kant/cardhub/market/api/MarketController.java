package com.kant.cardhub.market.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.market.application.MarketApplicationService;
import com.kant.cardhub.market.domain.MarketPriceStat;
import com.kant.cardhub.trade.domain.Trade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market/cards/{variantId}")
public class MarketController {

    private final MarketApplicationService marketService;

    public MarketController(MarketApplicationService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    public ApiResponse<MarketResponse> market(@PathVariable UUID variantId) {
        return ApiResponse.success(MarketResponse.from(marketService.get(variantId)));
    }

    @GetMapping("/trades")
    public ApiResponse<List<TradeResponse>> recentTrades(
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(marketService.recentTrades(variantId, limit).stream()
                .map(TradeResponse::from).toList());
    }

    public record MarketResponse(
            UUID cardVariantId,
            Long recentPrice,
            Long average7d,
            Long median30d,
            Long activeLowestPrice,
            long volume30d,
            Instant calculatedAt
    ) {
        static MarketResponse from(MarketPriceStat stat) {
            return new MarketResponse(
                    stat.getCardVariantId(), stat.getRecentPrice(), stat.getAverage7d(),
                    stat.getMedian30d(), stat.getActiveLowestPrice(), stat.getVolume30d(),
                    stat.getCalculatedAt());
        }
    }

    public record TradeResponse(UUID id, UUID listingId, long price, Instant occurredAt) {
        static TradeResponse from(Trade trade) {
            return new TradeResponse(trade.getId(), trade.getListingId(), trade.getPrice(), trade.getOccurredAt());
        }
    }
}
