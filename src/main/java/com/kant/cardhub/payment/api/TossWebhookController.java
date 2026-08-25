package com.kant.cardhub.payment.api;

import com.kant.cardhub.common.api.ApiResponse;
import com.kant.cardhub.payment.application.TossWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/toss")
public class TossWebhookController {

    private final TossWebhookService webhookService;

    public TossWebhookController(TossWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebhookAck>> receive(
            @RequestHeader(name = "tosspayments-webhook-transmission-id", required = false)
            String transmissionId,
            @RequestBody TossWebhookRequest request
    ) {
        boolean enqueued = webhookService.accept(
                transmissionId,
                request.eventType(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(new WebhookAck(enqueued)));
    }

    public record TossWebhookRequest(String eventType, Map<String, Object> data) {
    }

    public record WebhookAck(boolean reconciliationEnqueued) {
    }
}
