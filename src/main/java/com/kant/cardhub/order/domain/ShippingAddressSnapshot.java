package com.kant.cardhub.order.domain;

import java.util.Objects;

/**
 * Version 2 shipping address payload. The whole serialized value is encrypted before persistence.
 */
public record ShippingAddressSnapshot(
        String recipientName,
        String phoneNumber,
        String postalCode,
        String addressLine1,
        String addressLine2
) {
    public ShippingAddressSnapshot {
        recipientName = requireText(recipientName, "recipientName", 100);
        phoneNumber = requireText(phoneNumber, "phoneNumber", 30);
        postalCode = requireText(postalCode, "postalCode", 20);
        addressLine1 = requireText(addressLine1, "addressLine1", 500);
        addressLine2 = normalizeOptional(addressLine2, 500);
    }

    private static String requireText(String value, String name, int maxLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("addressLine2 is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
