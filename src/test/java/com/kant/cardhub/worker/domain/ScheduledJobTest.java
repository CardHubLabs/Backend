package com.kant.cardhub.worker.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledJobTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void retriesWithBackoffThenFailsAtMaxAttempts() {
        ScheduledJob job = ScheduledJob.schedule(
                UUID.randomUUID(), "TEST", "{}", "test:1", NOW, 2, NOW
        );

        job.claim("worker-a", NOW);
        job.fail("worker-a", "temporary", NOW);

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttempts()).isOne();
        assertThat(job.getAvailableAt()).isEqualTo(NOW.plusSeconds(2));

        job.claim("worker-a", NOW.plusSeconds(2));
        job.fail("worker-a", "still failing", NOW.plusSeconds(2));

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(2);
    }

    @Test
    void onlyLeaseOwnerCanCompleteJob() {
        ScheduledJob job = ScheduledJob.schedule(
                UUID.randomUUID(), "TEST", "{}", null, NOW, 3, NOW
        );
        job.claim("worker-a", NOW);

        assertThatThrownBy(() -> job.succeed("worker-b", NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
