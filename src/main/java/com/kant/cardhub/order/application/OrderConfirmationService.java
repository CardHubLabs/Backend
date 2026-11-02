package com.kant.cardhub.order.application;

import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventType;
import com.kant.cardhub.order.application.port.TradeCreationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderConfirmationService {

    private final OrderRepository orderRepository;
    private final TradeCreationPort tradeCreationPort;
    private final CommerceEventPublisher eventPublisher;
    private final Clock clock;

    public OrderConfirmationService(
            OrderRepository orderRepository,
            TradeCreationPort tradeCreationPort,
            CommerceEventPublisher eventPublisher,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.tradeCreationPort = tradeCreationPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> confirm(UUID buyerId, UUID orderId) {
        Order order = lock(orderId);
        Instant now = clock.instant();
        order.confirm(buyerId, now);
        return createTradesAndEvents(order, now);
    }

    @Transactional
    public List<UUID> autoConfirm(UUID orderId) {
        Order order = lock(orderId);
        Instant now = clock.instant();
        order.autoConfirm(now);
        return createTradesAndEvents(order, now);
    }

    private List<UUID> createTradesAndEvents(Order order, Instant now) {
        TradeCreationPort.ConfirmedOrder confirmed = new TradeCreationPort.ConfirmedOrder(
                order.getId(), order.getBuyerId(), order.getSellerId(),
                order.getItems().stream()
                        .map(item -> new TradeCreationPort.ConfirmedItem(
                                item.getId(), item.getListingId(), item.getCardVariantId(), item.getUnitPrice()))
                        .toList());
        List<UUID> tradeIds = tradeCreationPort.createTrades(confirmed, now);
        eventPublisher.publish(CommerceEvent.orderEvent(
                CommerceEventType.CONFIRMED, order.getBuyerId(), order.getId(), now));
        eventPublisher.publish(CommerceEvent.orderEvent(
                CommerceEventType.CONFIRMED, order.getSellerId(), order.getId(), now));
        return tradeIds;
    }

    private Order lock(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
