package com.kant.cardhub.admin.application;

import com.kant.cardhub.cart.infrastructure.CartItemRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.market.application.MarketApplicationService;
import com.kant.cardhub.offer.application.OfferService;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.application.RefundProcessingService;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.payment.infrastructure.RefundRepository;
import com.kant.cardhub.payment.application.PaymentReconcileJobHandler;
import com.kant.cardhub.worker.application.ScheduledJobService;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserRole;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.application.port.MarketplaceAccountLifecyclePort;
import com.kant.cardhub.user.infrastructure.UserRepository;
import com.kant.cardhub.trade.domain.Trade;
import com.kant.cardhub.trade.infrastructure.TradeRepository;
import com.kant.cardhub.shipment.application.ShipmentAutoConfirmationService;
import com.kant.cardhub.shipment.application.ShipmentTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger audit = LoggerFactory.getLogger("CARDHUB_ADMIN_AUDIT");

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final CartItemRepository cartItemRepository;
    private final OfferService offerService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ScheduledJobService scheduledJobService;
    private final RefundRepository refundRepository;
    private final RefundProcessingService refundProcessingService;
    private final TradeRepository tradeRepository;
    private final MarketApplicationService marketApplicationService;
    private final MarketplaceAccountLifecyclePort marketplaceLifecycle;
    private final ShipmentAutoConfirmationService shipmentAutoConfirmationService;
    private final ShipmentTrackingService shipmentTrackingService;
    private final Clock clock;

    public AdminService(
            UserRepository userRepository,
            ListingRepository listingRepository,
            CartItemRepository cartItemRepository,
            OfferService offerService,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ScheduledJobService scheduledJobService,
            RefundRepository refundRepository,
            RefundProcessingService refundProcessingService,
            TradeRepository tradeRepository,
            MarketApplicationService marketApplicationService,
            MarketplaceAccountLifecyclePort marketplaceLifecycle,
            ShipmentAutoConfirmationService shipmentAutoConfirmationService,
            ShipmentTrackingService shipmentTrackingService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.listingRepository = listingRepository;
        this.cartItemRepository = cartItemRepository;
        this.offerService = offerService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.scheduledJobService = scheduledJobService;
        this.refundRepository = refundRepository;
        this.refundProcessingService = refundProcessingService;
        this.tradeRepository = tradeRepository;
        this.marketApplicationService = marketApplicationService;
        this.marketplaceLifecycle = marketplaceLifecycle;
        this.shipmentAutoConfirmationService = shipmentAutoConfirmationService;
        this.shipmentTrackingService = shipmentTrackingService;
        this.clock = clock;
    }

    @Transactional
    public void suspendUser(UUID adminId, UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireMutableUser(adminId, user);
        if (userRepository.hasActiveTransactions(userId)) {
            throw new BusinessException(ErrorCode.ACTIVE_TRANSACTION_EXISTS);
        }
        marketplaceLifecycle.suspend(userId, Instant.now(clock));
        user.suspend();
        log(adminId, "USER_SUSPEND", userId);
    }

    @Transactional
    public void activateUser(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireMutableUser(adminId, user);
        user.activate();
        log(adminId, "USER_ACTIVATE", userId);
    }

    @Transactional
    public void hideListing(UUID adminId, UUID listingId) {
        blockListing(adminId, listingId, "Legacy admin hide endpoint");
    }

    @Transactional
    public void blockListing(UUID adminId, UUID listingId, String reason) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        listing.block(adminId, reason, Instant.now(clock));
        cartItemRepository.deleteAllByListingId(listingId);
        int expiredOffers = offerService.expireForBlockedListing(listingId, adminId);
        audit.info(
                "actorId={} action=LISTING_BLOCK targetId={} expiredOffers={}",
                adminId, listingId, expiredOffers
        );
    }

    @Transactional
    public void unblockListing(UUID adminId, UUID listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        listing.unblock(Instant.now(clock));
        log(adminId, "LISTING_UNBLOCK", listingId);
    }

    public RefundStatus approveRefund(UUID adminId, UUID refundId) {
        if (!refundRepository.existsById(refundId)) {
            throw new BusinessException(ErrorCode.REFUND_NOT_FOUND);
        }
        RefundStatus status = refundProcessingService.approve(refundId);
        audit.info("actorId={} action=REFUND_APPROVE targetId={} result={}", adminId, refundId, status);
        return status;
    }

    @Transactional
    public void releaseAutoConfirmHold(UUID adminId, UUID orderId) {
        shipmentAutoConfirmationService.releaseHold(orderId);
        log(adminId, "AUTO_CONFIRM_HOLD_RELEASE", orderId);
    }

    @Transactional
    public void markShipmentDelivered(UUID adminId, UUID orderId, Instant deliveredAt) {
        shipmentTrackingService.markDeliveredBySystem(orderId, deliveredAt);
        log(adminId, "SHIPMENT_MANUAL_DELIVERED", orderId);
    }

    @Transactional
    public void excludeTrade(UUID adminId, UUID tradeId, String reason) {
        Trade trade = findTrade(tradeId);
        trade.exclude(reason, Instant.now(clock));
        marketApplicationService.refresh(trade.getCardVariantId());
        log(adminId, "TRADE_EXCLUDE", tradeId);
    }

    @Transactional
    public void restoreTrade(UUID adminId, UUID tradeId) {
        Trade trade = findTrade(tradeId);
        trade.restore();
        marketApplicationService.refresh(trade.getCardVariantId());
        log(adminId, "TRADE_RESTORE", tradeId);
    }

    @Transactional(readOnly = true)
    public Page<User> users(int page, int size) {
        return userRepository.findAll(pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Listing> listings(int page, int size) {
        return listingRepository.findAll(pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Order> orders(int page, int size) {
        return orderRepository.findAll(pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Payment> payments(int page, int size) {
        return paymentRepository.findAll(pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Payment payment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    @Transactional
    public UUID enqueuePaymentReconcile(UUID adminId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제를 찾을 수 없습니다."));
        payment.queueManualReconcile(Instant.now(clock));
        String dedupeKey = "ADMIN_PAYMENT_RECONCILE:" + paymentId + ":" + (payment.getConfirmAttemptCount() + 1);
        UUID jobId = scheduledJobService.enqueue(
                        PaymentReconcileJobHandler.JOB_TYPE,
                        paymentId.toString(),
                        dedupeKey,
                        Instant.now(clock),
                        10
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "이미 같은 결제 복구 작업이 등록됐습니다."));
        audit.info("actorId={} action=PAYMENT_RECONCILE_ENQUEUE targetId={} jobId={}", adminId, paymentId, jobId);
        return jobId;
    }

    @Transactional(readOnly = true)
    public Page<Refund> refunds(int page, int size) {
        return refundRepository.findAll(pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Trade> trades(int page, int size) {
        return tradeRepository.findAll(pageable(page, size));
    }

    private Trade findTrade(UUID tradeId) {
        return tradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "거래를 찾을 수 없습니다."));
    }

    private void requireMutableUser(UUID adminId, User user) {
        if (adminId.equals(user.getId()) || user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 계정은 이 API로 상태를 변경할 수 없습니다.");
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.CONFLICT, "탈퇴한 사용자의 상태는 변경할 수 없습니다.");
        }
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private void log(UUID adminId, String action, UUID targetId) {
        audit.info("actorId={} action={} targetId={}", adminId, action, targetId);
    }
}
