package com.kant.cardhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RefundStatus status;

    @Column(name = "provider_code", length = 100)
    private String providerCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Refund() {
    }

    public static Refund request(UUID orderId, UUID paymentId, UUID buyerId, long amount, String reason, Instant now) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A refund reason of at most 500 characters is required");
        }
        Refund refund = new Refund();
        refund.id = UUID.randomUUID();
        refund.orderId = orderId;
        refund.paymentId = paymentId;
        refund.buyerId = buyerId;
        refund.amount = amount;
        refund.reason = reason;
        refund.status = RefundStatus.REQUESTED;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
    }

    public void start(Instant now) {
        if (status == RefundStatus.PROCESSING) {
            return;
        }
        if (status != RefundStatus.REQUESTED && status != RefundStatus.UNKNOWN) {
            throw new IllegalStateException("Refund cannot be started from " + status);
        }
        status = RefundStatus.PROCESSING;
        updatedAt = now;
    }

    public void retry(String reason, Instant now) {
        if (status != RefundStatus.FAILED) {
            throw new IllegalStateException("Only a failed refund can be requested again");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A refund reason of at most 500 characters is required");
        }
        this.reason = reason;
        this.status = RefundStatus.REQUESTED;
        this.providerCode = null;
        this.updatedAt = now;
    }

    public void approve(String code, Instant now) {
        requireResolving();
        status = RefundStatus.APPROVED;
        providerCode = code;
        updatedAt = now;
    }

    public void fail(String code, Instant now) {
        requireResolving();
        status = RefundStatus.FAILED;
        providerCode = code;
        updatedAt = now;
    }

    public void unknown(String code, Instant now) {
        requireResolving();
        status = RefundStatus.UNKNOWN;
        providerCode = code;
        updatedAt = now;
    }

    private void requireResolving() {
        if (status != RefundStatus.PROCESSING) {
            throw new IllegalStateException("Refund is not being resolved");
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getBuyerId() { return buyerId; }
    public long getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
}
