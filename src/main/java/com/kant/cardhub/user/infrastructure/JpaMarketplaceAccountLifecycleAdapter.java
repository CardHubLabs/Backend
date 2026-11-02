package com.kant.cardhub.user.infrastructure;

import com.kant.cardhub.user.application.port.MarketplaceAccountLifecyclePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class JpaMarketplaceAccountLifecycleAdapter implements MarketplaceAccountLifecyclePort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void suspend(UUID userId, Instant now) {
        hideActiveListings(userId, now);
        expireOpenOffers(userId, now);
    }

    @Override
    public void withdraw(UUID userId, Instant now) {
        entityManager.createNativeQuery("""
                        update listings
                           set status = 'DELETED', updated_at = :now, version = version + 1
                         where seller_id = :userId and status in ('ACTIVE', 'HIDDEN')
                        """)
                .setParameter("userId", userId)
                .setParameter("now", now)
                .executeUpdate();
        expireOpenOffers(userId, now);
        entityManager.createNativeQuery("delete from cart_items where user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("delete from favorites where user_id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        update price_alerts
                           set active = false, updated_at = :now, version = version + 1
                         where user_id = :userId and active = true
                        """)
                .setParameter("userId", userId)
                .setParameter("now", now)
                .executeUpdate();
    }

    private void hideActiveListings(UUID userId, Instant now) {
        entityManager.createNativeQuery("""
                        update listings
                           set status = 'HIDDEN', updated_at = :now, version = version + 1
                         where seller_id = :userId and status = 'ACTIVE'
                        """)
                .setParameter("userId", userId)
                .setParameter("now", now)
                .executeUpdate();
    }

    private void expireOpenOffers(UUID userId, Instant now) {
        entityManager.createNativeQuery("""
                        update offers
                           set status = 'EXPIRED', updated_at = :now, version = version + 1
                         where (seller_id = :userId or buyer_id = :userId)
                           and status in ('PENDING', 'COUNTERED', 'ACCEPTED')
                        """)
                .setParameter("userId", userId)
                .setParameter("now", now)
                .executeUpdate();
    }
}
