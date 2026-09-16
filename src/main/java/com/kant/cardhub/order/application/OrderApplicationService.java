package com.kant.cardhub.order.application;

import com.kant.cardhub.common.security.SensitiveDataCipher;
import com.kant.cardhub.order.application.port.AcceptedOfferPort;
import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.application.port.ListingSnapshot;
import com.kant.cardhub.order.application.port.PurchasedCartCleanupPort;
import com.kant.cardhub.order.application.port.OrderDeliveryProjectionPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.domain.ShippingAddressSnapshot;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final ListingReservationPort listingReservationPort;
    private final SensitiveDataCipher sensitiveDataCipher;
    private final AcceptedOfferPort acceptedOfferPort;
    private final PurchasedCartCleanupPort purchasedCartCleanupPort;
    private final ShippingAddressCodec shippingAddressCodec;
    private final OrderDeliveryProjectionPort orderDeliveryProjectionPort;
    private final Clock clock;

    public OrderApplicationService(
            OrderRepository orderRepository,
            ListingReservationPort listingReservationPort,
            SensitiveDataCipher sensitiveDataCipher,
            AcceptedOfferPort acceptedOfferPort,
            PurchasedCartCleanupPort purchasedCartCleanupPort,
            ShippingAddressCodec shippingAddressCodec,
            OrderDeliveryProjectionPort orderDeliveryProjectionPort,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.listingReservationPort = listingReservationPort;
        this.sensitiveDataCipher = sensitiveDataCipher;
        this.acceptedOfferPort = acceptedOfferPort;
        this.purchasedCartCleanupPort = purchasedCartCleanupPort;
        this.shippingAddressCodec = shippingAddressCodec;
        this.orderDeliveryProjectionPort = orderDeliveryProjectionPort;
        this.clock = clock;
    }

    @Transactional
    @Deprecated(since = "v1", forRemoval = false)
    public Order create(UUID buyerId, List<UUID> requestedListingIds, String shippingAddress) {
        return createInternal(buyerId, requestedListingIds, requireLegacyAddress(shippingAddress), (short) 1, null);
    }

    @Transactional
    public Order create(
            UUID buyerId,
            List<UUID> requestedListingIds,
            ShippingAddressSnapshot shippingAddress
    ) {
        return createInternal(
                buyerId, requestedListingIds, shippingAddressCodec.encode(shippingAddress), (short) 2, null);
    }

    @Transactional
    @Deprecated(since = "v1", forRemoval = false)
    public Order createSingle(
            UUID buyerId,
            UUID listingId,
            UUID acceptedOfferId,
            String shippingAddress
    ) {
        return createInternal(
                buyerId, List.of(listingId), requireLegacyAddress(shippingAddress), (short) 1, acceptedOfferId);
    }

    @Transactional
    public Order createSingle(
            UUID buyerId,
            UUID listingId,
            UUID acceptedOfferId,
            ShippingAddressSnapshot shippingAddress
    ) {
        return createInternal(
                buyerId, List.of(listingId), shippingAddressCodec.encode(shippingAddress), (short) 2, acceptedOfferId);
    }

    private Order createInternal(
            UUID buyerId,
            List<UUID> requestedListingIds,
            String serializedShippingAddress,
            short shippingAddressSchemaVersion,
            UUID acceptedOfferId
    ) {
        List<UUID> listingIds = normalizeListingIds(requestedListingIds);
        List<ListingSnapshot> listings = listingReservationPort.lockAvailable(buyerId, listingIds);
        UUID sellerId = listings.getFirst().sellerId();
        if (listings.stream().anyMatch(listing -> !sellerId.equals(listing.sellerId()))) {
            throw new IllegalArgumentException("A grouped order may contain listings from only one seller");
        }
        SensitiveDataCipher.EncryptedValue address = sensitiveDataCipher.encrypt(serializedShippingAddress);
        Instant now = clock.instant();
        Long acceptedPrice = acceptedOfferId == null
                ? null
                : acceptedOfferPort.consume(acceptedOfferId, buyerId, listings.getFirst().id());
        List<OrderItemDraft> drafts = listings.stream()
                .map(listing -> new OrderItemDraft(
                        listing.id(), listing.cardVariantId(), listing.title(),
                        acceptedPrice == null ? listing.price() : acceptedPrice,
                        listing.shippingFee(),
                        acceptedOfferId,
                        listing.cardCondition(),
                        listing.primaryImageStorageKey()))
                .toList();
        Order order = Order.create(
                buyerId, sellerId, drafts, address.ciphertext(), address.keyVersion(),
                shippingAddressSchemaVersion, now);
        orderRepository.saveAndFlush(order);
        listingReservationPort.reserveAll(listingIds, order.getId(), order.getReservationExpiresAt());
        purchasedCartCleanupPort.removePurchasedListings(buyerId, listingIds);
        return order;
    }

    @Transactional(readOnly = true)
    public Order getForParticipant(UUID actorId, UUID orderId) {
        Order order = get(orderId);
        if (!order.getBuyerId().equals(actorId) && !order.getSellerId().equals(actorId)) {
            throw new SecurityException("Only order participants may view an order");
        }
        order.getItems().size();
        return order;
    }

    @Transactional(readOnly = true)
    public OrderDetail getDetailForParticipant(UUID actorId, UUID orderId) {
        Order order = getForParticipant(actorId, orderId);
        boolean buyer = order.getBuyerId().equals(actorId);
        boolean sellerAfterPayment = order.getSellerId().equals(actorId)
                && order.getStatus() != com.kant.cardhub.order.domain.OrderStatus.PAYMENT_PENDING
                && order.getStatus() != com.kant.cardhub.order.domain.OrderStatus.CANCELLED;
        String legacyShippingAddress = null;
        ShippingAddressSnapshot shippingAddress = null;
        if (buyer || sellerAfterPayment) {
            String plaintext = sensitiveDataCipher.decrypt(new SensitiveDataCipher.EncryptedValue(
                    order.getShippingAddressCiphertext(),
                    order.getShippingAddressKeyVersion()
            ));
            if (order.getShippingAddressSchemaVersion() == 2) {
                shippingAddress = shippingAddressCodec.decode(plaintext);
            } else {
                legacyShippingAddress = plaintext;
            }
        }
        return new OrderDetail(
                order, legacyShippingAddress, shippingAddress,
                orderDeliveryProjectionPort.findByOrderId(orderId).orElse(null));
    }

    @Transactional
    public void cancel(UUID buyerId, UUID orderId) {
        Order order = lock(orderId);
        order.cancelByBuyer(buyerId, clock.instant());
        listingReservationPort.releaseOwned(listingIds(order), order.getId());
    }

    @Transactional(readOnly = true)
    public List<Order> purchases(UUID buyerId, int page, int size) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<Order> sales(UUID sellerId, int page, int size) {
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, PageRequest.of(page, size));
    }

    private Order get(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    private Order lock(UUID id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    private static List<UUID> normalizeListingIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("At least one listing is required");
        }
        List<UUID> normalized = ids.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        if (normalized.size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate listings are not allowed");
        }
        return normalized;
    }

    public static List<UUID> listingIds(Order order) {
        return order.getItems().stream().map(item -> item.getListingId()).toList();
    }

    private static String requireLegacyAddress(String value) {
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new IllegalArgumentException("shippingAddress is required and must be at most 2000 characters");
        }
        return value.trim();
    }

    public record OrderDetail(
            Order order,
            String legacyShippingAddress,
            ShippingAddressSnapshot shippingAddress,
            OrderDeliveryProjectionPort.DeliveryProjection deliveryProjection
    ) {
    }
}
