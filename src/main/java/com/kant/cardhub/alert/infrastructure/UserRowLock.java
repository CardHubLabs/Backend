package com.kant.cardhub.alert.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class UserRowLock {

    private final EntityManager entityManager;

    public UserRowLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Serializes the max-active-alert invariant for one user. */
    public boolean acquire(UUID userId) {
        return !entityManager.createNativeQuery(
                        "SELECT id FROM users WHERE id = :userId AND status = 'ACTIVE' FOR UPDATE")
                .setParameter("userId", userId)
                .getResultList()
                .isEmpty();
    }
}
