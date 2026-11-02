package com.kant.cardhub.order.api;

import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.common.error.ErrorResponse;
import com.kant.cardhub.common.error.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice(basePackages = {
        "com.kant.cardhub.order", "com.kant.cardhub.payment", "com.kant.cardhub.shipment",
        "com.kant.cardhub.trade", "com.kant.cardhub.market", "com.kant.cardhub.review"
})
public class CommerceExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ErrorResponse> forbidden(SecurityException exception, HttpServletRequest request) {
        return response(ErrorCode.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException exception, HttpServletRequest request) {
        return response(ErrorCode.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception, HttpServletRequest request) {
        return response(classifyIllegalArgument(exception.getMessage()), exception.getMessage(), request);
    }

    private ErrorCode classifyIllegalArgument(String message) {
        if (message == null) {
            return ErrorCode.BUSINESS_RULE_VIOLATION;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (!normalized.contains("not found") && !normalized.contains("does not exist")
                && !normalized.contains("no longer exists")) {
            return ErrorCode.BUSINESS_RULE_VIOLATION;
        }
        if (normalized.startsWith("order")) {
            return ErrorCode.ORDER_NOT_FOUND;
        }
        if (normalized.startsWith("payment")) {
            return ErrorCode.PAYMENT_NOT_FOUND;
        }
        if (normalized.startsWith("refund")) {
            return ErrorCode.REFUND_NOT_FOUND;
        }
        if (normalized.startsWith("shipment")) {
            return ErrorCode.SHIPMENT_NOT_FOUND;
        }
        if (normalized.startsWith("trade")) {
            return ErrorCode.TRADE_NOT_FOUND;
        }
        if (normalized.startsWith("review")) {
            return ErrorCode.REVIEW_NOT_FOUND;
        }
        return ErrorCode.RESOURCE_NOT_FOUND;
    }

    private ResponseEntity<ErrorResponse> response(
            ErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        String traceId = value == null ? "unknown" : value.toString();
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message, traceId));
    }
}
