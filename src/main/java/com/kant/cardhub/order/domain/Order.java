package com.kant.cardhub.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity(name = "CommerceOrder")
@Table(name = "orders")
public class Order {

    public static final Duration RESERVATION_DURATION = Duration.ofMinutes(10);

    @Id
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "item_subtotal", nullable = false)
    private long itemSubtotal;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "payment_in_flight", nullable = false)
    private boolean paymentInFlight;

    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    @Column(name = "shipping_address_ciphertext", nullable = false, columnDefinition = "text")
    private String shippingAddressCiphertext;

    @Column(name = "shipping_address_key_version", nullable = false, length = 64)
    private String shippingAddressKeyVersion;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order create(
            UUID buyerId,
            UUID sellerId,
            List<OrderItemDraft> drafts,
            String shippingAddressCiphertext,
            String shippingAddressKeyVersion,
            Instant now
    ) {
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(now, "now");
        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("A seller cannot buy their own listing");
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }
        if (drafts.stream().map(OrderItemDraft::listingId).distinct().count() != drafts.size()) {
            throw new IllegalArgumentException("Duplicate listings are not allowed");
        }
        if (shippingAddressCiphertext == null || shippingAddressCiphertext.isBlank()) {
            throw new IllegalArgumentException("Encrypted shipping address is required");
        }

        Order order = new Order();
        order.id = UUID.randomUUID();
        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.itemSubtotal = drafts.stream().mapToLong(OrderItemDraft::price).sum();
        order.shippingFee = drafts.stream().mapToLong(OrderItemDraft::shippingFee).max().orElse(0L);
        order.totalAmount = Math.addExact(order.itemSubtotal, order.shippingFee);
        order.status = OrderStatus.PAYMENT_PENDING;
        order.reservationExpiresAt = now.plus(RESERVATION_DURATION);
        order.shippingAddressCiphertext = shippingAddressCiphertext;
        order.shippingAddressKeyVersion = shippingAddressKeyVersion;
        order.createdAt = now;
        order.updatedAt = now;
        drafts.forEach(draft -> order.items.add(new OrderItem(order, draft, now)));
        return order;
    }

    public void beginPayment(UUID actorId, long requestedAmount, Instant now) {
        requireBuyer(actorId);
        requireStatus(OrderStatus.PAYMENT_PENDING);
        if (paymentInFlight) {
            throw new IllegalStateException("Payment is already in progress");
        }
        if (!now.isBefore(reservationExpiresAt)) {
            throw new IllegalStateException("Order reservation has expired");
        }
        if (requestedAmount != totalAmount) {
            throw new IllegalArgumentException("Payment amount does not match the order total");
        }
        paymentInFlight = true;
        touch(now);
    }

    public void markPaid(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        if (!paymentInFlight) {
            throw new IllegalStateException("Payment was not prepared");
        }
        status = OrderStatus.PAID;
        paymentInFlight = false;
        touch(now);
    }

    public void cancelByBuyer(UUID actorId, Instant now) {
        requireBuyer(actorId);
        cancelPending(now);
    }

    public void cancelExpired(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        if (paymentInFlight || now.isBefore(reservationExpiresAt)) {
            throw new IllegalStateException("Order cannot be expired");
        }
        status = OrderStatus.CANCELLED;
        touch(now);
    }

    public void cancelAfterDefinitivePaymentFailure(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        status = OrderStatus.CANCELLED;
        paymentInFlight = false;
        touch(now);
    }

    private void cancelPending(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING);
        if (paymentInFlight) {
            throw new IllegalStateException("An in-flight payment cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        touch(now);
    }

    public void markShipped(UUID actorId, Instant now) {
        requireSeller(actorId);
        requireStatus(OrderStatus.PAID);
        status = OrderStatus.SHIPPED;
        touch(now);
    }

    public void markDelivered(Instant now) {
        requireStatus(OrderStatus.SHIPPED);
        status = OrderStatus.DELIVERED;
        touch(now);
    }

    public void confirm(UUID actorId, Instant now) {
        requireBuyer(actorId);
        confirmSystem(now);
    }

    public void autoConfirm(Instant now) {
        confirmSystem(now);
    }

    private void confirmSystem(Instant now) {
        requireStatus(OrderStatus.DELIVERED);
        status = OrderStatus.CONFIRMED;
        confirmedAt = now;
        touch(now);
    }

    public void requestRefund(UUID actorId, Instant now) {
        requireBuyer(actorId);
        requireStatus(OrderStatus.PAID);
        status = OrderStatus.REFUND_REQUESTED;
        touch(now);
    }

    public void markRefunded(Instant now) {
        requireStatus(OrderStatus.REFUND_REQUESTED);
        status = OrderStatus.REFUNDED;
        touch(now);
    }

    public void rejectRefund(Instant now) {
        requireStatus(OrderStatus.REFUND_REQUESTED);
        status = OrderStatus.PAID;
        touch(now);
    }

    public void requireBuyer(UUID actorId) {
        if (!buyerId.equals(actorId)) {
            throw new SecurityException("Only the buyer may access this order operation");
        }
    }

    public void requireSeller(UUID actorId) {
        if (!sellerId.equals(actorId)) {
            throw new SecurityException("Only the seller may access this order operation");
        }
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected order status " + expected + " but was " + status);
        }
    }

    private void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() { return id; }
    public UUID getBuyerId() { return buyerId; }
    public UUID getSellerId() { return sellerId; }
    public long getItemSubtotal() { return itemSubtotal; }
    public long getShippingFee() { return shippingFee; }
    public long getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public boolean isPaymentInFlight() { return paymentInFlight; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getShippingAddressCiphertext() { return shippingAddressCiphertext; }
    public String getShippingAddressKeyVersion() { return shippingAddressKeyVersion; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
