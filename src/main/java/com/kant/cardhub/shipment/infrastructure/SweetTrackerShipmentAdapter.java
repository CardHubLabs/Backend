package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Comparator;

@Component
@ConditionalOnProperty(name = "cardhub.shipment.provider", havingValue = "sweettracker")
public class SweetTrackerShipmentAdapter implements ShipmentTrackingPort {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;
    private final String apiKey;
    private final Clock clock;

    public SweetTrackerShipmentAdapter(
            RestClient.Builder builder,
            SweetTrackerProperties properties,
            Clock clock
    ) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("SWEETTRACKER_API_KEY is required when SHIPMENT_PROVIDER=sweettracker");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalStateException("SWEETTRACKER_BASE_URL is required");
        }
        this.restClient = builder.clone()
                .requestFactory(requestFactory(properties))
                .baseUrl(properties.baseUrl())
                .build();
        this.apiKey = properties.apiKey();
        this.clock = clock;
    }

    static SimpleClientHttpRequestFactory requestFactory(SweetTrackerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    @Override
    public TrackingResult track(String carrierCode, String trackingNumber) {
        SweetTrackerResponse response = restClient.get()
                .uri(uri -> uri.path("/api/v1/trackingInfo")
                        .queryParam("t_key", apiKey)
                        .queryParam("t_code", carrierCode)
                        .queryParam("t_invoice", trackingNumber)
                        .build())
                .retrieve()
                .body(SweetTrackerResponse.class);
        if (response == null) {
            throw new IllegalStateException("SweetTracker returned an empty response");
        }
        if (response.result() != null && !"Y".equalsIgnoreCase(response.result())) {
            throw new IllegalStateException("SweetTracker rejected tracking lookup");
        }

        TrackingDetail latest = latest(response.trackingDetails());
        int level = latest != null && latest.level() != null
                ? latest.level()
                : response.level() == null ? 0 : response.level();
        ShipmentStatus status = normalizeStatus(response.completeYN(), level, latest);
        Instant eventAt = parseTime(latest == null ? null : latest.timeString());
        String description = description(latest, status);
        String eventKey = sha256(carrierCode + '|' + trackingNumber + '|' + level + '|'
                + eventAt + '|' + description);
        return new TrackingResult(
                carrierCode, trackingNumber, status, eventKey, description, eventAt);
    }

    private ShipmentStatus normalizeStatus(String complete, int level, TrackingDetail latest) {
        if ("Y".equalsIgnoreCase(complete) || level >= 6) {
            return ShipmentStatus.DELIVERED;
        }
        if (isDeliveryException(latest)) {
            return ShipmentStatus.EXCEPTION;
        }
        if (level >= 2) {
            return ShipmentStatus.IN_TRANSIT;
        }
        return ShipmentStatus.READY;
    }

    private boolean isDeliveryException(TrackingDetail detail) {
        if (detail == null) {
            return false;
        }
        String text = ((detail.kind() == null ? "" : detail.kind()) + ' '
                + (detail.where() == null ? "" : detail.where())).toLowerCase(java.util.Locale.ROOT);
        return text.contains("반송") || text.contains("분실") || text.contains("파손")
                || text.contains("배송불가") || text.contains("사고") || text.contains("보류")
                || text.contains("exception") || text.contains("return");
    }

    private TrackingDetail latest(List<TrackingDetail> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.stream()
                .filter(detail -> parseProviderTime(detail.timeString()) != null)
                .max(Comparator.comparing(detail -> parseProviderTime(detail.timeString())))
                .orElse(details.getLast());
    }

    private String description(TrackingDetail detail, ShipmentStatus status) {
        if (detail == null) {
            return "SweetTracker status: " + status;
        }
        String kind = detail.kind() == null ? status.name() : detail.kind();
        return detail.where() == null || detail.where().isBlank()
                ? kind
                : kind + " (" + detail.where() + ")";
    }

    private Instant parseTime(String value) {
        Instant parsed = parseProviderTime(value);
        return parsed == null ? clock.instant() : parsed;
    }

    private Instant parseProviderTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, EVENT_TIME).atZone(KOREA).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record SweetTrackerResponse(
            String result,
            String completeYN,
            Integer level,
            List<TrackingDetail> trackingDetails
    ) {
    }

    private record TrackingDetail(
            String timeString,
            String where,
            String kind,
            Integer level
    ) {
    }
}
