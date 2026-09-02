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
        UUID variantId = listingPriceReader.activeListingVariant(listingId).orElse(listingId);
        validateCreation(userId, variantId, listingId);
        OptionalPrice baseline = resolveBaseline(variantId, listingId);
        return createInternal(userId, variantId, listingId, baseline.value(), requestedThreshold);
    }

    @Transactional
    public PriceAlert createForVariant(UUID userId, UUID variantId, Integer requestedThreshold) {
        validateCreation(userId, variantId, null);
        long baselinePrice = listingPriceReader.currentActiveLowestPrice(variantId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "활성 매물이 없어 가격 알림 기준가를 계산할 수 없습니다."
                ));
        return createInternal(userId, variantId, null, baselinePrice, requestedThreshold);
    }

    private PriceAlert createInternal(
            UUID userId,
            UUID variantId,
            UUID legacyListingId,
            long baselinePrice,
            Integer requestedThreshold
    ) {
        int threshold = requestedThreshold == null
                ? PriceAlert.DEFAULT_THRESHOLD_PERCENTAGE
                : requestedThreshold;

        try {
            return priceAlertRepository.saveAndFlush(new PriceAlert(
                    UUID.randomUUID(), userId, variantId, legacyListingId,
                    baselinePrice, threshold, Instant.now(clock)
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_ALREADY_EXISTS);
        }
    }

    private void validateCreation(UUID userId, UUID variantId, UUID legacyListingId) {
        if (!userRowLock.acquire(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (priceAlertRepository.existsByUserIdAndCardVariantIdAndActiveTrue(userId, variantId)
                || (legacyListingId != null
                && priceAlertRepository.existsByUserIdAndListingIdAndActiveTrue(userId, legacyListingId))) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_ALREADY_EXISTS);
        }
        if (priceAlertRepository.countByUserIdAndActiveTrue(userId) >= MAX_ACTIVE_ALERTS) {
            throw new BusinessException(ErrorCode.PRICE_ALERT_LIMIT_EXCEEDED);
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

    /** Recalculates the active lowest price and evaluates every alert for the card variant. */
    @Transactional
    public int evaluateVariant(UUID cardVariantId) {
        java.util.OptionalLong lowestPrice = listingPriceReader.currentActiveLowestPrice(cardVariantId);
        if (lowestPrice.isEmpty()) {
            return 0;
        }
        long currentPrice = lowestPrice.getAsLong();
        List<PriceAlert> alerts = priceAlertRepository
                .findAllByCardVariantIdAndActiveTrueOrderByCreatedAtAsc(cardVariantId);
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
                    "관심 카드의 최저가가 내려갔어요",
                    "등록 당시 최저가보다 " + alert.getThresholdPercentage() + "% 이상 낮아졌습니다.",
                    "CARD_VARIANT",
                    cardVariantId,
                    "PRICE_ALERT:" + alert.getId()
            ));
            fired++;
        }
        return fired;
    }

    private OptionalPrice resolveBaseline(UUID variantId, UUID listingId) {
        java.util.OptionalLong variantLowest = listingPriceReader.currentActiveLowestPrice(variantId);
        if (variantLowest.isPresent()) {
            return new OptionalPrice(variantLowest.getAsLong());
        }
        long legacyPrice = listingPriceReader.currentActivePrice(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return new OptionalPrice(legacyPrice);
    }

    private record OptionalPrice(long value) {
    }
}
