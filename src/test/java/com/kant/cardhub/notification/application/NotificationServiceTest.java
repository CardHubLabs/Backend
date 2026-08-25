package com.kant.cardhub.notification.application;

import com.kant.cardhub.notification.domain.NotificationType;
import com.kant.cardhub.notification.infrastructure.NotificationInsertRepository;
import com.kant.cardhub.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void reportsDuplicateNotificationAsNoOp() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationInsertRepository insertRepository = mock(NotificationInsertRepository.class);
        NotificationService service = new NotificationService(
                repository, insertRepository, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        NotificationCommand command = new NotificationCommand(
                UUID.randomUUID(), NotificationType.PAYMENT_DONE, "결제 완료", "결제가 완료됐습니다.",
                "ORDER", UUID.randomUUID(), "PAYMENT_DONE:order:user"
        );
        when(insertRepository.insert(any(), eq(command), eq(NOW))).thenReturn(false);

        Optional<UUID> result = service.send(command);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsGeneratedIdWhenInserted() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationInsertRepository insertRepository = mock(NotificationInsertRepository.class);
        NotificationService service = new NotificationService(
                repository, insertRepository, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        NotificationCommand command = new NotificationCommand(
                UUID.randomUUID(), NotificationType.SHIPPED, "발송", "상품이 발송됐습니다.",
                "ORDER", UUID.randomUUID(), null
        );
        when(insertRepository.insert(any(), eq(command), eq(NOW))).thenReturn(true);

        assertThat(service.send(command)).isPresent();
    }
}

