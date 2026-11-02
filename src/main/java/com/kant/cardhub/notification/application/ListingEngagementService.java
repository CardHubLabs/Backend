package com.kant.cardhub.notification.application;

import com.kant.cardhub.alert.application.PriceAlertService;
import com.kant.cardhub.notification.domain.NotificationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListingEngagementService {

    private final JdbcTemplate jdbcTemplate;
    private final PriceAlertService priceAlertService;
    private final NotificationService notificationService;

    public ListingEngagementService(
            JdbcTemplate jdbcTemplate,
            PriceAlertService priceAlertService,
            NotificationService notificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.priceAlertService = priceAlertService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void onPublished(UUID listingId, UUID variantId, UUID sellerId, long price) {
        notifyVariantFollowers(listingId, variantId, sellerId);
        priceAlertService.evaluateVariant(variantId);
        notifyUndercutSellers(listingId, variantId, sellerId, price);
    }

    @Transactional
    public void onPriceChanged(
            UUID listingId,
            UUID variantId,
            UUID sellerId,
            long previousPrice,
            long currentPrice
    ) {
        priceAlertService.evaluateVariant(variantId);
        if (currentPrice < previousPrice) {
            notifyUndercutSellers(listingId, variantId, sellerId, currentPrice);
        }
    }

    private void notifyVariantFollowers(UUID listingId, UUID variantId, UUID sellerId) {
        List<UUID> recipients = jdbcTemplate.queryForList("""
                SELECT DISTINCT f.user_id
                  FROM favorites f
                 WHERE f.card_variant_id = ?
                   AND f.user_id <> ?
                """, UUID.class, variantId, sellerId);

        for (UUID recipientId : recipients) {
            notificationService.send(new NotificationCommand(
                    recipientId,
                    NotificationType.LISTING_ALERT,
                    "관심 카드의 새 매물이 등록됐어요",
                    "관심 있게 본 카드와 같은 종류의 매물을 확인해 보세요.",
                    "LISTING",
                    listingId,
                    "LISTING_ALERT:" + listingId + ":" + recipientId
            ));
        }
    }

    private void notifyUndercutSellers(UUID listingId, UUID variantId, UUID sellerId, long price) {
        List<UUID> recipients = jdbcTemplate.queryForList("""
                SELECT DISTINCT seller_id
                  FROM listings
                 WHERE card_variant_id = ?
                   AND status = 'ACTIVE'
                   AND moderation_status = 'CLEAR'
                   AND price > ?
                   AND id <> ?
                   AND seller_id <> ?
                """, UUID.class, variantId, price, listingId, sellerId);

        for (UUID recipientId : recipients) {
            notificationService.send(new NotificationCommand(
                    recipientId,
                    NotificationType.UNDERCUT,
                    "더 낮은 가격의 매물이 등록됐어요",
                    "같은 카드의 최저가가 변경되었습니다.",
                    "LISTING",
                    listingId,
                    "UNDERCUT:" + listingId + ":" + price + ":" + recipientId
            ));
        }
    }
}
