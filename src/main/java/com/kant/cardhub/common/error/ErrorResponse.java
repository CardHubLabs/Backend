package com.kant.cardhub.common.error;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String traceId,
        List<FieldErrorDetail> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.name(), message, traceId, List.of());
    }

    public static ErrorResponse validation(String traceId, List<FieldErrorDetail> errors) {
        return new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                traceId,
                List.copyOf(errors)
        );
    }
}
