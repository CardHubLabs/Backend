package com.kant.cardhub.worker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_jobs")
public class ScheduledJob {

    @Id
    private UUID id;

    @Column(name = "job_type", nullable = false, length = 80, updatable = false)
    private String jobType;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "dedupe_key", length = 200, updatable = false)
    private String dedupeKey;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduledJob() {
    }

    public static ScheduledJob schedule(
            UUID id,
            String jobType,
            String payload,
            String dedupeKey,
            Instant availableAt,
            int maxAttempts,
            Instant now
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        ScheduledJob job = new ScheduledJob();
        job.id = id;
        job.jobType = jobType;
        job.payload = payload;
        job.status = JobStatus.PENDING;
        job.dedupeKey = dedupeKey;
        job.availableAt = availableAt;
        job.maxAttempts = maxAttempts;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void claim(String workerId, Instant now) {
        if (status != JobStatus.PENDING || availableAt.isAfter(now)) {
            throw new IllegalStateException("job is not claimable");
        }
        status = JobStatus.RUNNING;
        lockedAt = now;
        lockedBy = workerId;
        attempts++;
        updatedAt = now;
    }

    public void succeed(String workerId, Instant now) {
        requireOwner(workerId);
        status = JobStatus.SUCCEEDED;
        lockedAt = null;
        lockedBy = null;
        lastError = null;
        updatedAt = now;
    }

    public void fail(String workerId, String error, Instant now) {
        requireOwner(workerId);
        lastError = sanitize(error);
        lockedAt = null;
        lockedBy = null;
        updatedAt = now;
        if (attempts >= maxAttempts) {
            status = JobStatus.FAILED;
            return;
        }
        status = JobStatus.PENDING;
        availableAt = now.plus(backoff(attempts));
    }

    public void failPermanently(String workerId, String error, Instant now) {
        requireOwner(workerId);
        status = JobStatus.FAILED;
        lastError = sanitize(error);
        lockedAt = null;
        lockedBy = null;
        updatedAt = now;
    }

    private void requireOwner(String workerId) {
        if (status != JobStatus.RUNNING || !workerId.equals(lockedBy)) {
            throw new IllegalStateException("worker does not own the job lease");
        }
    }

    private Duration backoff(int currentAttempt) {
        long seconds = Math.min(300L, 1L << Math.min(currentAttempt, 8));
        return Duration.ofSeconds(seconds);
    }

    private String sanitize(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown job failure";
        }
        String oneLine = error.replace('\n', ' ').replace('\r', ' ');
        return oneLine.substring(0, Math.min(oneLine.length(), 1000));
    }

    public UUID getId() {
        return id;
    }

    public String getJobType() {
        return jobType;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getLastError() {
        return lastError;
    }
}
