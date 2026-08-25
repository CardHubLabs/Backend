package com.kant.cardhub.alert.application;

import com.kant.cardhub.alert.domain.PriceAlert;
import com.kant.cardhub.alert.infrastructure.PriceAlertRepository;
import com.kant.cardhub.alert.infrastructure.UserRowLock;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.notification.application.NotificationCommand;
import com.kant.cardhub.notification.application.NotificationService;
import com.kant.cardhub.notification.domain.NotificationType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PriceAlertService {

    public static final int MAX_ACTIVE_ALERTS = 20;

    private final PriceAlertRepository priceAlertRepository;
    private final ListingPriceReader listingPriceReader;
    private final UserRowLock userRowLock;
    private final NotificationService notificationService;
    private final Clock clock;

    public PriceAlertService(
            PriceAlertRepository priceAlertRepository,
            ListingPriceReader listingPriceReader,
            UserRowLock userRowLock,
            NotificationService notificationService,
            Clock clock
    ) {
        this.priceAlertRepository = priceAlertRepository;
        this.listingPriceReader = listingPriceReader;
        this.userRowLock = userRowLock;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public PriceAlert create(UUID userId, UUID listingId, Integer requestedThreshold) {
        if (!userRowLock.acquire(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (priceAlertRepository.existsByUserIdAndListingIdAndActiveTrue(userId, listingId)) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_ALREADY_EXISTS);
        }
        if (priceAlertRepository.countByUserIdAndActiveTrue(userId) >= MAX_ACTIVE_ALERTS) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_LIMIT_EXCEEDED);
        }

        long baselinePrice = listingPriceReader.currentActivePrice(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        int threshold = requestedThreshold == null
                ? PriceAlert.DEFAULT_THRESHOLD_PERCENTAGE
                : requestedThreshold;

        try {
            return priceAlertRepository.saveAndFlush(new PriceAlert(
                    UUID.randomUUID(), userId, listingId, baselinePrice, threshold, Instant.now(clock)
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void cancel(UUID userId, UUID alertId) {
        PriceAlert alert = priceAlertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_ALERT_NOT_FOUND));
        alert.cancel(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<PriceAlert> list(UUID userId, int page, int size) {
        return priceAlertRepository.findAllByUserId(
                userId,
                PageRequest.of(
                        Math.max(page, 0),
                        Math.clamp(size, 1, 50),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                )
        );
    }

    /** Called by listing price changes or a reconciliation job. */
    @Transactional
    public int fireEligibleAlerts(UUID listingId, long currentPrice) {
        List<PriceAlert> alerts = priceAlertRepository
                .findAllByListingIdAndActiveTrueOrderByCreatedAtAsc(listingId);
        Instant now = Instant.now(clock);
        int fired = 0;
        for (PriceAlert alert : alerts) {
            if (!alert.shouldTrigger(currentPrice)) {
                continue;
            }
            alert.trigger(currentPrice, now);
            notificationService.send(new NotificationCommand(
                    alert.getUserId(),
                    NotificationType.PRICE_ALERT,
                    "관심 매물의 가격이 내려갔어요",
                    "등록 당시 가격보다 " + alert.getThresholdPercentage() + "% 이상 낮아졌습니다.",
                    "LISTING",
                    listingId,
                    "PRICE_ALERT:" + alert.getId()
            ));
            fired++;
        }
        return fired;
    }
}

