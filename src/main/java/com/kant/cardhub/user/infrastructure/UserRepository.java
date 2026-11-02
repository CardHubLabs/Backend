package com.kant.cardhub.user.infrastructure;

import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    java.util.Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    @Query("select user.status from User user where user.id = :userId")
    java.util.Optional<UserStatus> findStatusById(@Param("userId") UUID userId);

    @Query("""
            select case when user.birthDate is not null and user.termsAgreedAt is not null
                        then true else false end
              from User user
             where user.id = :userId
            """)
    java.util.Optional<Boolean> findOnboardingCompletedById(@Param("userId") UUID userId);

    @Query(value = """
            select case when count(*) > 0 then true else false end
              from orders
             where (buyer_id = :userId or seller_id = :userId)
               and status in ('PAYMENT_PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'REFUND_REQUESTED')
            """, nativeQuery = true)
    boolean hasActiveTransactions(@Param("userId") UUID userId);
}
