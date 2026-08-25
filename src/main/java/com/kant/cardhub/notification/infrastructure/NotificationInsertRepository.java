package com.kant.cardhub.notification.infrastructure;

import com.kant.cardhub.notification.application.NotificationCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class NotificationInsertRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationInsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * PostgreSQL's conflict handling makes notification emission idempotent without
     * poisoning the surrounding domain transaction on a duplicate event.
     */
    public boolean insert(UUID id, NotificationCommand command, Instant createdAt) {
        int inserted = jdbcTemplate.update("""
                        INSERT INTO notifications (
                            id, user_id, type, title, message, reference_type,
                            reference_id, dedupe_key, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (dedupe_key) DO NOTHING
                        """,
                id,
                command.userId(),
                command.type().name(),
                command.title(),
                command.message(),
                command.referenceType(),
                command.referenceId(),
                command.dedupeKey(),
                Timestamp.from(createdAt)
        );
        return inserted == 1;
    }
}

