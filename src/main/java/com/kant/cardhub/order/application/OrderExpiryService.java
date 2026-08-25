package com.kant.cardhub.order.application;

import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final ListingReservationPort listingReservationPort;
    private final Clock clock;

    public OrderExpiryService(OrderRepository orderRepository, ListingReservationPort listingReservationPort, Clock clock) {
        this.orderRepository = orderRepository;
        this.listingReservationPort = listingReservationPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> findExpired(int limit) {
        return orderRepository.findExpirableIds(
                OrderStatus.PAYMENT_PENDING, clock.instant(), PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAYMENT_PENDING || order.isPaymentInFlight()) {
            return false;
        }
        Instant now = clock.instant();
        if (now.isBefore(order.getReservationExpiresAt())) {
            return false;
        }
        order.cancelExpired(now);
        listingReservationPort.releaseOwned(OrderApplicationService.listingIds(order), order.getId());
        return true;
    }
}
