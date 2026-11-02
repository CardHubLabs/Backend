package com.kant.cardhub.storage.infrastructure;

import com.kant.cardhub.storage.domain.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    @Query(value = """
            SELECT *
              FROM upload_sessions
             WHERE status = 'PENDING'
               AND expires_at <= :now
             ORDER BY expires_at, id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UploadSession> lockExpiredPending(
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}
