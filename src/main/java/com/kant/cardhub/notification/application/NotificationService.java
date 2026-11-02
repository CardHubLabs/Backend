package com.kant.cardhub.notification.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.notification.domain.Notification;
import com.kant.cardhub.notification.infrastructure.NotificationInsertRepository;
import com.kant.cardhub.notification.infrastructure.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationInsertRepository notificationInsertRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationInsertRepository notificationInsertRepository,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationInsertRepository = notificationInsertRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<UUID> send(NotificationCommand command) {
        validate(command);
        UUID notificationId = UUID.randomUUID();
        boolean inserted = notificationInsertRepository.insert(notificationId, command, Instant.now(clock));
        return inserted ? Optional.of(notificationId) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return unreadOnly
                ? notificationRepository.findAllByUserIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findAllByUserId(userId, pageable);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now(clock));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now(clock));
    }

    private void validate(NotificationCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.userId(), "userId");
        Objects.requireNonNull(command.type(), "type");
        requireText(command.title(), 100, "title");
        requireText(command.message(), 500, "message");
        if (command.referenceType() != null && command.referenceType().length() > 40) {
            throw new IllegalArgumentException("referenceType must be at most 40 characters");
        }
        if (command.dedupeKey() != null && command.dedupeKey().length() > 200) {
            throw new IllegalArgumentException("dedupeKey must be at most 200 characters");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}

