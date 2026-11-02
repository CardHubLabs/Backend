package com.kant.cardhub.trade.application;

import com.kant.cardhub.order.application.port.TradeCreationPort;
import com.kant.cardhub.payment.application.port.RefundTradePort;
import com.kant.cardhub.trade.domain.Trade;
import com.kant.cardhub.trade.infrastructure.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TradeApplicationService implements TradeCreationPort, RefundTradePort {

    private final TradeRepository tradeRepository;

    public TradeApplicationService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    @Transactional
    public List<UUID> createTrades(ConfirmedOrder order, Instant occurredAt) {
        List<UUID> result = new ArrayList<>(order.items().size());
        for (ConfirmedItem item : order.items()) {
            Trade trade = tradeRepository.findByOrderItemId(item.orderItemId()).orElseGet(() ->
                    tradeRepository.save(Trade.create(
                            order.orderId(), item.orderItemId(), item.listingId(), item.cardVariantId(),
                            order.buyerId(), order.sellerId(), item.price(), occurredAt)));
            result.add(trade.getId());
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public void excludeOrder(UUID orderId, String reason, Instant excludedAt) {
        tradeRepository.findByOrderId(orderId)
                .forEach(trade -> trade.exclude(reason, excludedAt));
    }

    @Transactional
    public void exclude(UUID tradeId, String reason, Instant now) {
        tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + tradeId))
                .exclude(reason, now);
    }

    @Transactional
    public void restore(UUID tradeId) {
        tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + tradeId))
                .restore();
    }
}
