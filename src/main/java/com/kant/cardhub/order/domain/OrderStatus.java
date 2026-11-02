package com.kant.cardhub.order.domain;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CONFIRMED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}
