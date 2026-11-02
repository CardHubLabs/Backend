package com.kant.cardhub.worker.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.worker.domain.ScheduledJob;
import com.kant.cardhub.worker.infrastructure.ScheduledJobInsertRepository;
import com.kant.cardhub.worker.infrastructure.ScheduledJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduledJobService {

    private final ScheduledJobRepository scheduledJobRepository;
    private final ScheduledJobInsertRepository scheduledJobInsertRepository;
    private final Clock clock;

    public ScheduledJobService(
            ScheduledJobRepository scheduledJobRepository,
            ScheduledJobInsertRepository scheduledJobInsertRepository,
            Clock clock
    ) {
        this.scheduledJobRepository = scheduledJobRepository;
        this.scheduledJobInsertRepository = scheduledJobInsertRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<UUID> enqueue(
            String jobType,
            String payload,
            String dedupeKey,
            Instant availableAt,
            int maxAttempts
    ) {
        validate(jobType, payload, dedupeKey, maxAttempts);
        UUID id = UUID.randomUUID();
        boolean inserted = scheduledJobInsertRepository.insert(
                id, jobType, payload, dedupeKey, availableAt, maxAttempts, Instant.now(clock)
        );
        return inserted ? Optional.of(id) : Optional.empty();
    }

    @Transactional
    public List<ScheduledJob> claimDue(String workerId, int batchSize) {
        Instant now = Instant.now(clock);
        List<ScheduledJob> jobs = scheduledJobRepository.lockDueJobs(now, Math.clamp(batchSize, 1, 100));
        jobs.forEach(job -> job.claim(workerId, now));
        return List.copyOf(jobs);
    }

    @Transactional
    public void succeed(UUID jobId, String workerId) {
        find(jobId).succeed(workerId, Instant.now(clock));
    }

    @Transactional
    public void fail(UUID jobId, String workerId, Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        find(jobId).fail(workerId, message, Instant.now(clock));
    }

    @Transactional
    public void failPermanently(UUID jobId, String workerId, String message) {
        find(jobId).failPermanently(workerId, message, Instant.now(clock));
    }

    @Transactional
    public int recoverStaleLeases(Duration leaseTimeout) {
        Instant now = Instant.now(clock);
        return scheduledJobRepository.recoverStaleLeases(now.minus(leaseTimeout), now);
    }

    private ScheduledJob find(UUID id) {
        return scheduledJobRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }

    private void validate(String type, String payload, String dedupeKey, int maxAttempts) {
        if (type == null || type.isBlank() || type.length() > 80) {
            throw new IllegalArgumentException("jobType must be non-blank and at most 80 characters");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (dedupeKey != null && dedupeKey.length() > 200) {
            throw new IllegalArgumentException("dedupeKey must be at most 200 characters");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }
}

