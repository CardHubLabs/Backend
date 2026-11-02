package com.kant.cardhub.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ErrorResponse> handleBinding(Exception exception, HttpServletRequest request) {
        var binding = exception instanceof MethodArgumentNotValidException manve
                ? manve.getBindingResult()
                : ((BindException) exception).getBindingResult();
        List<FieldErrorDetail> errors = binding.getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId(request), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(error -> new FieldErrorDetail(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId(request), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleMalformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.MALFORMED_REQUEST,
                ErrorCode.MALFORMED_REQUEST.defaultMessage(),
                traceId(request)
        ));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ErrorResponse> handleForbidden(SecurityException exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ErrorResponse.of(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.defaultMessage(),
                traceId(request)
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.status()).body(ErrorResponse.of(
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(),
                traceId(request)
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.BUSINESS_RULE_VIOLATION.status()).body(ErrorResponse.of(
                ErrorCode.BUSINESS_RULE_VIOLATION,
                exception.getMessage() == null ? ErrorCode.BUSINESS_RULE_VIOLATION.defaultMessage() : exception.getMessage(),
                traceId(request)
        ));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> handlePersistenceConflict(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.CONFLICT.status()).body(ErrorResponse.of(
                ErrorCode.CONFLICT,
                ErrorCode.CONFLICT.defaultMessage(),
                traceId(request)
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure traceId={}", traceId(request), exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                traceId(request)
        ));
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
