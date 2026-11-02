package com.kant.cardhub.notification.infrastructure;

import com.kant.cardhub.notification.application.NotificationCommand;
import com.kant.cardhub.notification.application.NotificationService;
import com.kant.cardhub.notification.domain.NotificationType;
import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventPort;
import org.springframework.stereotype.Component;

@Component
public class CommerceNotificationAdapter implements CommerceEventPort {

    private final NotificationService notificationService;

    public CommerceNotificationAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void publish(CommerceEvent event) {
        NotificationType type = NotificationType.valueOf(event.type().name());
        notificationService.send(new NotificationCommand(
                event.recipientId(),
                type,
                title(type),
                message(type),
                "ORDER",
                event.orderId(),
                event.dedupeKey()
        ));
    }

    private String title(NotificationType type) {
        return switch (type) {
            case PAYMENT_DONE -> "결제가 완료됐어요";
            case SALE_MADE -> "판매가 성사됐어요";
            case SHIPPED -> "상품이 발송됐어요";
            case CONFIRMED -> "구매가 확정됐어요";
            case AUTO_CONFIRM_NOTICE -> "곧 구매가 자동 확정돼요";
            default -> throw new IllegalArgumentException("Unsupported commerce notification: " + type);
        };
    }

    private String message(NotificationType type) {
        return switch (type) {
            case PAYMENT_DONE -> "주문 결제가 정상적으로 완료되었습니다.";
            case SALE_MADE -> "등록한 상품의 주문이 결제되었습니다.";
            case SHIPPED -> "판매자가 운송장 정보를 등록했습니다.";
            case CONFIRMED -> "거래가 구매 확정 상태로 변경되었습니다.";
            case AUTO_CONFIRM_NOTICE -> "배송 완료 후 7일이 지나면 구매가 자동 확정됩니다.";
            default -> throw new IllegalArgumentException("Unsupported commerce notification: " + type);
        };
    }
}

