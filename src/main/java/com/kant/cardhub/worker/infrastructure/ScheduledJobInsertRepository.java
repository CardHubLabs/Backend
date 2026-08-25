package com.kant.cardhub.worker.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class ScheduledJobInsertRepository {

    private final JdbcTemplate jdbcTemplate;

    public ScheduledJobInsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(
            UUID id,
            String jobType,
            String payload,
            String dedupeKey,
            Instant availableAt,
            int maxAttempts,
            Instant now
    ) {
        return jdbcTemplate.update("""
                        INSERT INTO scheduled_jobs (
                            id, job_type, payload, status, dedupe_key, available_at,
                            attempts, max_attempts, created_at, updated_at
                        ) VALUES (?, ?, ?, 'PENDING', ?, ?, 0, ?, ?, ?)
                        ON CONFLICT (dedupe_key) DO NOTHING
                        """,
                id,
                jobType,
                payload,
                dedupeKey,
                Timestamp.from(availableAt),
                maxAttempts,
                Timestamp.from(now),
                Timestamp.from(now)
        ) == 1;
    }
}

