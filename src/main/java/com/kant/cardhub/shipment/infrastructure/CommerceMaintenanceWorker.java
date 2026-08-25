package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.order.application.OrderExpiryService;
import com.kant.cardhub.payment.application.PaymentApplicationService;
import com.kant.cardhub.payment.application.RefundProcessingService;
import com.kant.cardhub.shipment.application.ShipmentAutoConfirmationService;
import com.kant.cardhub.shipment.application.ShipmentTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "cardhub.worker.enabled", havingValue = "true")
public class CommerceMaintenanceWorker {

    private static final Logger log = LoggerFactory.getLogger(CommerceMaintenanceWorker.class);
    private static final int BATCH_SIZE = 100;

    private final OrderExpiryService orderExpiryService;
    private final PaymentApplicationService paymentService;
    private final RefundProcessingService refundService;
    private final ShipmentTrackingService trackingService;
    private final ShipmentAutoConfirmationService autoConfirmationService;

    public CommerceMaintenanceWorker(
            OrderExpiryService orderExpiryService,
            PaymentApplicationService paymentService,
            RefundProcessingService refundService,
            ShipmentTrackingService trackingService,
            ShipmentAutoConfirmationService autoConfirmationService
    ) {
        this.orderExpiryService = orderExpiryService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.trackingService = trackingService;
        this.autoConfirmationService = autoConfirmationService;
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.order-expiry-delay:5000}")
    public void expireOrders() {
        forEachSafely("order-expiry", orderExpiryService.findExpired(BATCH_SIZE), orderExpiryService::expireOne);
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.payment-reconcile-delay:30000}")
    public void reconcilePayments() {
        forEachSafely("payment-reconcile", paymentService.findUnknown(BATCH_SIZE), paymentService::reconcile);
        forEachSafely("refund-reconcile", refundService.findRecoverable(BATCH_SIZE), refundService::approve);
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.shipment-tracking-delay:60000}")
    public void trackShipments() {
        forEachSafely("shipment-track", trackingService.findDue(BATCH_SIZE), trackingService::poll);
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.auto-confirm-delay:60000}")
    public void processAutoConfirmation() {
        forEachSafely("auto-confirm-notice", autoConfirmationService.findNoticeDue(BATCH_SIZE),
                autoConfirmationService::sendNotice);
        forEachSafely("auto-confirm", autoConfirmationService.findAutoConfirmDue(BATCH_SIZE),
                autoConfirmationService::autoConfirm);
    }

    private void forEachSafely(String task, Iterable<UUID> ids, Consumer<UUID> operation) {
        for (UUID id : ids) {
            try {
                operation.accept(id);
            } catch (RuntimeException failure) {
                log.warn("Commerce worker task={} target={} failed", task, id, failure);
            }
        }
    }
}
