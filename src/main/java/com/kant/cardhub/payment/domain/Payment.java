package com.kant.cardhub.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
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

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 24)
    private PaymentMethod paymentMethod;

    @Column(name = "easy_pay_provider", length = 100)
    private String easyPayProvider;

    @Column(name = "confirm_attempt_count", nullable = false)
    private int confirmAttemptCount;

    @Column(name = "last_confirm_attempt_at")
    private Instant lastConfirmAttemptAt;

    @Column(name = "next_reconcile_at")
    private Instant nextReconcileAt;

    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired;

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
        payment.confirmAttemptCount = 1;
        payment.lastConfirmAttemptAt = now;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void approve(String providerCode, Instant approvedAt, Instant now) {
        approve(providerCode, null, null, null, approvedAt, now);
    }

    public void approve(
            String providerCode,
            String providerStatus,
            String method,
            String easyPayProvider,
            Instant approvedAt,
            Instant now
    ) {
        requirePendingResolution();
        status = PaymentStatus.APPROVED;
        this.providerCode = providerCode;
        this.providerStatus = providerStatus;
        this.paymentMethod = PaymentMethod.fromProvider(method);
        this.easyPayProvider = normalize(easyPayProvider);
        this.approvedAt = approvedAt == null ? now : approvedAt;
        lastReconciledAt = now;
        nextReconcileAt = null;
        manualReviewRequired = false;
        updatedAt = now;
    }

    public void fail(String providerCode, Instant now) {
        requirePendingResolution();
        status = PaymentStatus.FAILED;
        this.providerCode = providerCode;
        lastReconciledAt = now;
        nextReconcileAt = null;
        updatedAt = now;
    }

    public void unknown(String providerCode, Instant now) {
        unknown(providerCode, null, null, null, now, false);
    }

    public void unknown(
            String providerCode,
            String providerStatus,
            String method,
            String easyPayProvider,
            Instant now,
            boolean requireManualReview
    ) {
        if (status == PaymentStatus.APPROVED || status == PaymentStatus.FAILED) {
            return;
        }
        status = PaymentStatus.UNKNOWN;
        this.providerCode = providerCode;
        this.providerStatus = providerStatus;
        PaymentMethod parsedMethod = PaymentMethod.fromProvider(method);
        if (parsedMethod != null) {
            this.paymentMethod = parsedMethod;
        }
        if (easyPayProvider != null) {
            this.easyPayProvider = normalize(easyPayProvider);
        }
        lastReconciledAt = now;
        manualReviewRequired = manualReviewRequired || requireManualReview || confirmAttemptCount >= 10;
        nextReconcileAt = manualReviewRequired ? null : now.plus(reconcileDelay(confirmAttemptCount));
        updatedAt = now;
    }

    public void beginConfirmRetry(Instant now) {
        if (status != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("Only an UNKNOWN payment can be retried immediately");
        }
        if (manualReviewRequired) {
            throw new IllegalStateException("Payment requires manual review");
        }
        status = PaymentStatus.CONFIRMING;
        confirmAttemptCount = Math.addExact(confirmAttemptCount, 1);
        lastConfirmAttemptAt = now;
        nextReconcileAt = null;
        updatedAt = now;
    }

    public void beginReconcileAttempt(Instant now) {
        if (status != PaymentStatus.UNKNOWN && status != PaymentStatus.CONFIRMING) {
            throw new IllegalStateException("Payment is not recoverable");
        }
        if (manualReviewRequired) {
            throw new IllegalStateException("Payment requires manual review");
        }
        status = PaymentStatus.CONFIRMING;
        confirmAttemptCount = Math.addExact(confirmAttemptCount, 1);
        lastConfirmAttemptAt = now;
        nextReconcileAt = null;
        updatedAt = now;
    }

    public void requireManualReview(String providerCode, Instant now) {
        if (status == PaymentStatus.APPROVED || status == PaymentStatus.FAILED) {
            return;
        }
        status = PaymentStatus.UNKNOWN;
        this.providerCode = providerCode;
        this.manualReviewRequired = true;
        this.nextReconcileAt = null;
        this.lastReconciledAt = now;
        this.updatedAt = now;
    }

    public void queueManualReconcile(Instant now) {
        if (status != PaymentStatus.UNKNOWN && status != PaymentStatus.CONFIRMING) {
            throw new IllegalStateException("Only an unresolved payment can be reconciled");
        }
        manualReviewRequired = false;
        status = PaymentStatus.UNKNOWN;
        nextReconcileAt = now;
        updatedAt = now;
    }

    public boolean providerIdentityMatches(String resultPaymentKey, String resultOrderId, Long resultAmount) {
        return paymentKey.equals(resultPaymentKey)
                && tossOrderId.equals(resultOrderId)
                && resultAmount != null
                && amount == resultAmount;
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
    public String getProviderStatus() { return providerStatus; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getEasyPayProvider() { return easyPayProvider; }
    public int getConfirmAttemptCount() { return confirmAttemptCount; }
    public Instant getLastConfirmAttemptAt() { return lastConfirmAttemptAt; }
    public Instant getNextReconcileAt() { return nextReconcileAt; }
    public boolean isManualReviewRequired() { return manualReviewRequired; }
    public Instant getLastReconciledAt() { return lastReconciledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static Duration reconcileDelay(int attemptCount) {
        long seconds = Math.min(3_600L, 30L << Math.min(Math.max(attemptCount - 1, 0), 7));
        return Duration.ofSeconds(seconds);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
