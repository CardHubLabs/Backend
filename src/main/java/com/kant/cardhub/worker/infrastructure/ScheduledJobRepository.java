package com.kant.cardhub.worker.infrastructure;

import com.kant.cardhub.worker.domain.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, UUID> {

    @Query(value = """
            SELECT *
              FROM scheduled_jobs
             WHERE status = 'PENDING'
               AND available_at <= :now
             ORDER BY available_at, created_at, id
             FOR UPDATE SKIP LOCKED
             LIMIT :batchSize
            """, nativeQuery = true)
    List<ScheduledJob> lockDueJobs(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE scheduled_jobs
               SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
                   available_at = :now,
                   locked_at = NULL,
                   locked_by = NULL,
                   last_error = 'Recovered an expired worker lease',
                   updated_at = :now
             WHERE status = 'RUNNING'
               AND locked_at < :staleBefore
            """, nativeQuery = true)
    int recoverStaleLeases(
            @Param("staleBefore") Instant staleBefore,
            @Param("now") Instant now
    );
}
