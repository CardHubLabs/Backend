package com.kant.cardhub.payment.domain;

public enum PaymentMethod {
    CARD,
    EASY_PAY;

    public static PaymentMethod fromProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "CARD", "카드" -> CARD;
            case "EASY_PAY", "EASYPAY", "간편결제" -> EASY_PAY;
            default -> null;
        };
    }
}
