package com.kant.cardhub.alert.application;

import com.kant.cardhub.alert.domain.PriceAlert;
import com.kant.cardhub.alert.infrastructure.PriceAlertRepository;
import com.kant.cardhub.alert.infrastructure.UserRowLock;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.notification.application.NotificationCommand;
import com.kant.cardhub.notification.application.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    private PriceAlertRepository repository;
    private ListingPriceReader listingPriceReader;
    private UserRowLock userRowLock;
    private NotificationService notificationService;
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        repository = mock(PriceAlertRepository.class);
        listingPriceReader = mock(ListingPriceReader.class);
        userRowLock = mock(UserRowLock.class);
        notificationService = mock(NotificationService.class);
        service = new PriceAlertService(
                repository,
                listingPriceReader,
                userRowLock,
                notificationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void serializesAndRejectsTwentyFirstActiveAlert() {
        UUID userId = UUID.randomUUID();
        when(userRowLock.acquire(userId)).thenReturn(true);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(20L);

        assertThatThrownBy(() -> service.create(userId, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.PRICE_ALERT_LIMIT_EXCEEDED));

        verify(listingPriceReader, never()).currentActivePrice(any());
    }

    @Test
    void firesEligibleAlertsAndEmitsOneNotification() {
        UUID listingId = UUID.randomUUID();
        PriceAlert eligible = new PriceAlert(
                UUID.randomUUID(), UUID.randomUUID(), listingId, 10_000, 10, NOW
        );
        PriceAlert ineligible = new PriceAlert(
                UUID.randomUUID(), UUID.randomUUID(), listingId, 10_000, 20, NOW
        );
        when(repository.findAllByListingIdAndActiveTrueOrderByCreatedAtAsc(listingId))
                .thenReturn(List.of(eligible, ineligible));

        int fired = service.fireEligibleAlerts(listingId, 9_000);

        assertThat(fired).isOne();
        assertThat(eligible.isActive()).isFalse();
        assertThat(ineligible.isActive()).isTrue();
        verify(notificationService).send(any(NotificationCommand.class));
    }

    @Test
    void createsWithDefaultTenPercentThreshold() {
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(userRowLock.acquire(userId)).thenReturn(true);
        when(repository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
        when(listingPriceReader.currentActivePrice(listingId)).thenReturn(OptionalLong.of(15_000));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PriceAlert result = service.create(userId, listingId, null);

        assertThat(result.getThresholdPercentage()).isEqualTo(10);
        assertThat(result.getBaselinePrice()).isEqualTo(15_000);
    }
}
