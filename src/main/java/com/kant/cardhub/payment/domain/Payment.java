package com.kant.cardhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "payment_key", nullable = false, length = 200)
    private String paymentKey;

    @Column(name = "toss_order_id", nullable = false, unique = true, length = 64)
    private String tossOrderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;

    @Column(name = "provider_code", length = 100)
    private String providerCode;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    public static Payment prepare(
            UUID orderId,
            UUID buyerId,
            String idempotencyKey,
            String paymentKey,
            String tossOrderId,
            long amount,
            Instant now
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || paymentKey == null || paymentKey.isBlank()
                || tossOrderId == null || tossOrderId.isBlank()) {
            throw new IllegalArgumentException("Payment identifiers are required");
        }
        if (idempotencyKey.length() > 100 || paymentKey.length() > 200 || tossOrderId.length() > 64) {
            throw new IllegalArgumentException("Payment identifiers are too long");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = Objects.requireNonNull(orderId);
        payment.buyerId = Objects.requireNonNull(buyerId);
        payment.idempotencyKey = idempotencyKey;
        payment.paymentKey = paymentKey;
        payment.tossOrderId = tossOrderId;
        payment.amount = amount;
        payment.status = PaymentStatus.CONFIRMING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void approve(String providerCode, Instant approvedAt, Instant now) {
        requirePendingResolution();
        status = PaymentStatus.APPROVED;
        this.providerCode = providerCode;
        this.approvedAt = approvedAt == null ? now : approvedAt;
        lastReconciledAt = now;
        updatedAt = now;
    }

    public void fail(String providerCode, Instant now) {
        requirePendingResolution();
        status = PaymentStatus.FAILED;
        this.providerCode = providerCode;
        lastReconciledAt = now;
        updatedAt = now;
    }

    public void unknown(String providerCode, Instant now) {
        if (status == PaymentStatus.APPROVED || status == PaymentStatus.FAILED) {
            return;
        }
        status = PaymentStatus.UNKNOWN;
        this.providerCode = providerCode;
        lastReconciledAt = now;
        updatedAt = now;
    }

    private void requirePendingResolution() {
        if (status == PaymentStatus.APPROVED || status == PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment already has a definitive result");
        }
    }

    public boolean requestMatches(UUID orderId, UUID buyerId, String paymentKey, String tossOrderId, long amount) {
        return this.orderId.equals(orderId)
                && this.buyerId.equals(buyerId)
                && this.paymentKey.equals(paymentKey)
                && this.tossOrderId.equals(tossOrderId)
                && this.amount == amount;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getBuyerId() { return buyerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPaymentKey() { return paymentKey; }
    public String getTossOrderId() { return tossOrderId; }
    public long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderCode() { return providerCode; }
    public Instant getApprovedAt() { return approvedAt; }
}
