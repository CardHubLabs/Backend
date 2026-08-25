package com.kant.cardhub.notification.application;

import com.kant.cardhub.notification.domain.NotificationType;

import java.util.UUID;

public record NotificationCommand(
        UUID userId,
        NotificationType type,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        String dedupeKey
) {
}

