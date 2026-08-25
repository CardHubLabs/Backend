package com.kant.cardhub.order.infrastructure;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.application.port.ListingSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JpaListingReservationAdapter implements ListingReservationPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ListingSnapshot> lockAvailable(UUID buyerId, List<UUID> listingIds) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        List<?> sellerRows = entityManager.createNativeQuery("""
                        select distinct seller_id
                          from listings
                         where id in (:listingIds)
                        """)
                .setParameter("listingIds", listingIds)
                .getResultList();
        List<UUID> sellerIds = sellerRows.stream()
                .map(value -> value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value)))
                .toList();
        if (sellerIds.size() > 1) {
            throw new BusinessException(ErrorCode.MIXED_SELLER_ORDER);
        }
        if (sellerIds.size() == 1) {
            List<UUID> participantIds = java.util.stream.Stream.of(buyerId, sellerIds.getFirst())
                    .distinct()
                    .sorted()
                    .toList();
            @SuppressWarnings("unchecked")
            List<Object[]> participantRows = entityManager.createNativeQuery("""
                            select id, status
                              from users
                             where id in (:participantIds)
                             order by id
                             for update
                            """)
                    .setParameter("participantIds", participantIds)
                    .getResultList();
            if (participantRows.size() != participantIds.size()
                    || participantRows.stream().anyMatch(row -> !"ACTIVE".equals(String.valueOf(row[1])))) {
                throw new BusinessException(
                        ErrorCode.LISTING_INVALID_STATE,
                        "구매자 또는 판매자가 현재 거래 가능한 상태가 아닙니다."
                );
            }
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select l.id, l.seller_id, l.card_variant_id,
                               c.name, l.price, l.shipping_fee, l.status
                          from listings l
                          join card_variants cv on cv.id = l.card_variant_id
                          join cards c on c.id = cv.card_id
                         where l.id in (:listingIds)
                         order by l.id
                         for update
                        """)
                .setParameter("listingIds", listingIds)
                .getResultList();

        Map<UUID, ListingSnapshot> byId = new HashMap<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            String status = String.valueOf(row[6]);
            if (!"ACTIVE".equals(status)) {
                throw new IllegalStateException("Listing " + id + " is not available");
            }
            byId.put(id, new ListingSnapshot(
                    id,
                    (UUID) row[1],
                    (UUID) row[2],
                    String.valueOf(row[3]),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).longValue()
            ));
        }
        if (byId.size() != listingIds.size()) {
            throw new IllegalArgumentException("One or more listings do not exist");
        }
        List<ListingSnapshot> ordered = new ArrayList<>(listingIds.size());
        listingIds.forEach(id -> ordered.add(byId.get(id)));
        return ordered;
    }

    @Override
    public void reserveAll(List<UUID> listingIds, UUID orderId, Instant expiresAt) {
        int updated = entityManager.createNativeQuery("""
                        update listings
                           set status = 'RESERVED', reservation_order_id = :orderId,
                               reservation_expires_at = :expiresAt, version = version + 1
                         where id in (:listingIds) and status = 'ACTIVE'
                        """)
                .setParameter("orderId", orderId)
                .setParameter("expiresAt", expiresAt)
                .setParameter("listingIds", listingIds)
                .executeUpdate();
        if (updated != listingIds.size()) {
            throw new IllegalStateException("Failed to reserve every listing");
        }
    }

    @Override
    public void releaseOwned(List<UUID> listingIds, UUID orderId) {
        entityManager.createNativeQuery("""
                        update listings
                           set status = 'ACTIVE', reservation_order_id = null,
                               reservation_expires_at = null, version = version + 1
                         where id in (:listingIds) and status = 'RESERVED'
                           and reservation_order_id = :orderId
                        """)
                .setParameter("listingIds", listingIds)
                .setParameter("orderId", orderId)
                .executeUpdate();
    }

    @Override
    public void markSold(List<UUID> listingIds, UUID orderId) {
        int updated = entityManager.createNativeQuery("""
                        update listings
                           set status = 'SOLD', reservation_order_id = null,
                               reservation_expires_at = null, version = version + 1
                         where id in (:listingIds) and status = 'RESERVED'
                           and reservation_order_id = :orderId
                        """)
                .setParameter("listingIds", listingIds)
                .setParameter("orderId", orderId)
                .executeUpdate();
        if (updated != listingIds.size()) {
            throw new IllegalStateException("Listing reservation ownership changed before payment finalization");
        }
    }

    @Override
    public void reactivateSold(List<UUID> listingIds) {
        entityManager.createNativeQuery("""
                        update listings
                           set status = 'ACTIVE', reservation_order_id = null,
                               reservation_expires_at = null, version = version + 1
                         where id in (:listingIds) and status = 'SOLD'
                        """)
                .setParameter("listingIds", listingIds)
                .executeUpdate();
    }
}
