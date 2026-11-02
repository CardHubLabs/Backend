package com.kant.cardhub.payment.infrastructure;

import com.kant.cardhub.payment.application.port.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.net.http.HttpClient;

@Component
@ConditionalOnProperty(name = "cardhub.payment.provider", havingValue = "toss")
public class TossPaymentGateway implements PaymentGateway {

    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]{1,100})\\\"");
    private static final Set<String> DEFINITIVE_CONFIRM_DECLINES = Set.of(
            "PAY_PROCESS_CANCELED",
            "PAY_PROCESS_ABORTED",
            "REJECT_CARD_PAYMENT",
            "INVALID_CARD_EXPIRATION",
            "INVALID_STOPPED_CARD",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT"
    );

    private final RestClient restClient;
    private final Clock clock;

    public TossPaymentGateway(RestClient.Builder builder, TossPaymentProperties properties, Clock clock) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("TOSS_SECRET_KEY is required when PAYMENT_PROVIDER=toss");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("TOSS_BASE_URL is required when PAYMENT_PROVIDER=toss");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(properties.secretKey(), "");
                    headers.set(HttpHeaders.ACCEPT, "application/json");
                })
                .build();
        this.clock = clock;
    }

    @Override
    public PaymentGatewayResult confirm(ConfirmRequest request) {
        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .body(new ConfirmBody(request.paymentKey(), request.tossOrderId(), request.amount()))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return map(response);
        } catch (RestClientResponseException exception) {
            return mapConfirmFailure(exception);
        } catch (ResourceAccessException exception) {
            return PaymentGatewayResult.unknown("TOSS_NETWORK_ERROR");
        }
    }

    @Override
    public PaymentGatewayResult lookup(String paymentKey) {
        try {
            TossPaymentResponse response = restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return map(response);
        } catch (RestClientResponseException exception) {
            return mapLookupFailure(exception);
        } catch (ResourceAccessException exception) {
            return PaymentGatewayResult.unknown("TOSS_NETWORK_ERROR");
        }
    }

    @Override
    public PaymentGatewayResult cancel(CancelRequest request) {
        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", request.paymentKey())
                    .header("Idempotency-Key", request.idempotencyKey())
                    .body(new CancelBody(request.reason(), request.amount()))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            return classifyCancelResponse(response);
        } catch (RestClientResponseException exception) {
            return mapCancelFailure(exception);
        } catch (ResourceAccessException exception) {
            return PaymentGatewayResult.unknown("TOSS_NETWORK_ERROR");
        }
    }

    PaymentGatewayResult map(TossPaymentResponse response) {
        if (response == null || response.status() == null) {
            return PaymentGatewayResult.unknown("TOSS_EMPTY_RESPONSE");
        }
        if ("DONE".equals(response.status()) || "APPROVED".equals(response.status())) {
            return PaymentGatewayResult.providerApproved(
                    response.status(), parseInstant(response.approvedAt()),
                    response.paymentKey(), response.orderId(), response.totalAmount(),
                    response.method(), response.easyPay() == null ? null : response.easyPay().provider(),
                    response.status()
            );
        }
        PaymentGatewayResult classified = classifyProviderStatus(
                response.status(), response.approvedAt(), response.balanceAmount());
        if (classified.status() == ResultStatus.UNKNOWN) {
            return PaymentGatewayResult.providerUnknown(
                    classified.providerCode(), response.paymentKey(), response.orderId(),
                    response.totalAmount(), response.method(),
                    response.easyPay() == null ? null : response.easyPay().provider(), response.status());
        }
        return classified;
    }

    PaymentGatewayResult classifyProviderStatus(String status, String approvedAt, Long balanceAmount) {
        if (status == null) {
            return PaymentGatewayResult.unknown("TOSS_EMPTY_RESPONSE");
        }
        return switch (status) {
            case "DONE", "APPROVED" -> PaymentGatewayResult.approved(
                    status, parseInstant(approvedAt));
            case "ABORTED", "EXPIRED" ->
                    PaymentGatewayResult.declined(status);
            case "CANCELED" -> balanceAmount != null && balanceAmount == 0L
                    ? PaymentGatewayResult.declined(status)
                    : PaymentGatewayResult.unknown("TOSS_CANCELLATION_AMOUNT_UNVERIFIED");
            case "PARTIAL_CANCELED" -> PaymentGatewayResult.unknown(status);
            default -> PaymentGatewayResult.unknown(status);
        };
    }

    private PaymentGatewayResult mapConfirmFailure(RestClientResponseException exception) {
        String code = providerCode(exception.getResponseBodyAsString());
        return classifyConfirmFailure(code);
    }

    PaymentGatewayResult classifyConfirmFailure(String code) {
        return DEFINITIVE_CONFIRM_DECLINES.contains(code)
                ? PaymentGatewayResult.declined(code)
                : PaymentGatewayResult.unknown(code);
    }

    private PaymentGatewayResult mapLookupFailure(RestClientResponseException exception) {
        String code = providerCode(exception.getResponseBodyAsString());
        return classifyLookupFailure(exception.getStatusCode().value(), code);
    }

    PaymentGatewayResult classifyLookupFailure(int httpStatus, String code) {
        if (httpStatus == 404 || "NOT_FOUND_PAYMENT".equals(code)) {
            return PaymentGatewayResult.unknown(code);
        }
        return PaymentGatewayResult.unknown(code);
    }

    private PaymentGatewayResult mapCancelFailure(RestClientResponseException exception) {
        String code = providerCode(exception.getResponseBodyAsString());
        if ("ALREADY_CANCELED_PAYMENT".equals(code)) {
            return PaymentGatewayResult.approved(code, clock.instant());
        }
        return PaymentGatewayResult.unknown(code);
    }

    PaymentGatewayResult classifyCancelResponse(TossPaymentResponse response) {
        if (response != null && "CANCELED".equals(response.status())
                && response.balanceAmount() != null && response.balanceAmount() == 0L) {
            return PaymentGatewayResult.approved(response.status(), clock.instant());
        }
        if (response != null && ("CANCELED".equals(response.status())
                || "PARTIAL_CANCELED".equals(response.status()))) {
            return PaymentGatewayResult.unknown("TOSS_REFUND_AMOUNT_UNVERIFIED");
        }
        return map(response);
    }

    private String providerCode(String body) {
        if (body == null) {
            return "TOSS_ERROR";
        }
        Matcher matcher = ERROR_CODE.matcher(body);
        return matcher.find() ? matcher.group(1) : "TOSS_ERROR";
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return clock.instant();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            return clock.instant();
        }
    }

    private record ConfirmBody(String paymentKey, String orderId, long amount) {
    }

    private record CancelBody(String cancelReason, long cancelAmount) {
    }

    record TossPaymentResponse(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String method,
            TossEasyPay easyPay,
            String status,
            String approvedAt,
            Long balanceAmount
    ) {
        TossPaymentResponse(String status, String approvedAt, Long balanceAmount) {
            this(null, null, null, null, null, status, approvedAt, balanceAmount);
        }
    }

    record TossEasyPay(String provider) {
    }
}
