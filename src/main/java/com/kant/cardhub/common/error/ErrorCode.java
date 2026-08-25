package com.kant.cardhub.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태와 충돌하는 요청입니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "처리할 수 없는 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 사용자입니다."),
    ONBOARDING_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "가입 정보를 완성해야 합니다."),
    UNDERAGE(HttpStatus.UNPROCESSABLE_CONTENT, "만 15세 이상만 가입할 수 있습니다."),
    ACTIVE_TRANSACTION_EXISTS(HttpStatus.CONFLICT, "진행 중인 거래가 있어 탈퇴할 수 없습니다."),
    AUTH_SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "인증 세션을 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 refresh token입니다."),

    CATALOG_NOT_FOUND(HttpStatus.NOT_FOUND, "카탈로그 항목을 찾을 수 없습니다."),
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "매물을 찾을 수 없습니다."),
    LISTING_INVALID_STATE(HttpStatus.CONFLICT, "현재 매물 상태에서는 처리할 수 없습니다."),
    LISTING_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약된 매물입니다."),
    CART_ITEM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 장바구니에 있는 매물입니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "가격 제안을 찾을 수 없습니다."),
    OFFER_INVALID_STATE(HttpStatus.CONFLICT, "현재 제안 상태에서는 처리할 수 없습니다."),
    OFFER_FORBIDDEN(HttpStatus.FORBIDDEN, "제안을 처리할 권한이 없습니다."),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_INVALID_STATE(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    MIXED_SELLER_ORDER(HttpStatus.UNPROCESSABLE_CONTENT, "동일 판매자의 매물만 함께 주문할 수 있습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
    SHIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "배송 정보를 찾을 수 없습니다."),
    SHIPMENT_INVALID_STATE(HttpStatus.CONFLICT, "현재 배송 상태에서는 처리할 수 없습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 작성한 주문입니다."),

    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 관심 상품으로 등록했습니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "관심 상품을 찾을 수 없습니다."),
    PRICE_ALERT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "활성 가격 알림은 최대 20개까지 등록할 수 있습니다."),
    PRICE_ALERT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 활성 가격 알림이 있습니다."),
    PRICE_ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "가격 알림을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
