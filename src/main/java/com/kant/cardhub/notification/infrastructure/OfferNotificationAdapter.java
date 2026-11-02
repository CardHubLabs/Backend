package com.kant.cardhub.notification.infrastructure;

import com.kant.cardhub.notification.application.NotificationCommand;
import com.kant.cardhub.notification.application.NotificationService;
import com.kant.cardhub.notification.domain.NotificationType;
import com.kant.cardhub.offer.application.OfferEventPort;
import com.kant.cardhub.offer.domain.OfferStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OfferNotificationAdapter implements OfferEventPort {

    private final NotificationService notificationService;

    public OfferNotificationAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onReply(UUID recipientId, UUID offerId, OfferStatus status, String dedupeKey) {
        notificationService.send(new NotificationCommand(
                recipientId,
                NotificationType.OFFER_REPLY,
                title(status),
                message(status),
                "OFFER",
                offerId,
                dedupeKey
        ));
    }

    @Override
    public void onExpired(UUID recipientId, UUID offerId, String dedupeKey) {
        notificationService.send(new NotificationCommand(
                recipientId,
                NotificationType.OFFER_EXPIRED,
                "가격 제안이 만료됐어요",
                "응답 기한이 지나 가격 제안이 자동으로 종료되었습니다.",
                "OFFER",
                offerId,
                dedupeKey
        ));
    }

    private String title(OfferStatus status) {
        return switch (status) {
            case ACCEPTED -> "가격 제안이 수락됐어요";
            case REJECTED -> "가격 제안이 거절됐어요";
            case COUNTERED -> "새로운 역제안이 도착했어요";
            case EXPIRED -> "가격 제안이 만료됐어요";
            case CONSUMED -> "가격 제안으로 주문이 생성됐어요";
            case PENDING -> "가격 제안에 답변이 도착했어요";
        };
    }

    private String message(OfferStatus status) {
        return switch (status) {
            case ACCEPTED -> "수락된 가격으로 예약이 끝나기 전에 주문해 주세요.";
            case REJECTED -> "상대방이 가격 제안을 거절했습니다.";
            case COUNTERED -> "상대방이 새로운 가격을 제안했습니다.";
            case EXPIRED -> "응답 기한이 지나 가격 제안이 종료되었습니다.";
            case CONSUMED -> "수락된 가격 제안이 주문에 사용되었습니다.";
            case PENDING -> "가격 제안의 상태를 확인해 주세요.";
        };
    }
}
