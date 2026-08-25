package com.kant.cardhub.notification.infrastructure;

import com.kant.cardhub.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.userId = :userId
               and n.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
