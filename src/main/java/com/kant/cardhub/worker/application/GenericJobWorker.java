package com.kant.cardhub.worker.application;

import com.kant.cardhub.worker.domain.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "cardhub.worker.enabled", havingValue = "true")
public class GenericJobWorker {

    private static final Logger log = LoggerFactory.getLogger(GenericJobWorker.class);

    private final ScheduledJobService scheduledJobService;
    private final Map<String, ScheduledJobHandler> handlers;
    private final String workerId;
    private final int batchSize;
    private final Duration leaseTimeout;

    public GenericJobWorker(
            ScheduledJobService scheduledJobService,
            List<ScheduledJobHandler> handlers,
            @Value("${cardhub.worker.id:${HOSTNAME:local}-${random.uuid}}") String workerId,
            @Value("${cardhub.worker.batch-size:20}") int batchSize,
            @Value("${cardhub.worker.lease-timeout:PT5M}") Duration leaseTimeout
    ) {
        this.scheduledJobService = scheduledJobService;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                ScheduledJobHandler::jobType,
                Function.identity()
        ));
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.leaseTimeout = leaseTimeout;
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.poll-delay:1000}")
    public void poll() {
        for (ScheduledJob job : scheduledJobService.claimDue(workerId, batchSize)) {
            execute(job);
        }
    }

    @Scheduled(fixedDelayString = "${cardhub.worker.recovery-delay:60000}")
    public void recoverExpiredLeases() {
        int recovered = scheduledJobService.recoverStaleLeases(leaseTimeout);
        if (recovered > 0) {
            log.warn("Recovered {} expired job leases", recovered);
        }
    }

    private void execute(ScheduledJob job) {
        ScheduledJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            scheduledJobService.failPermanently(
                    job.getId(), workerId, "No handler registered for " + job.getJobType()
            );
            return;
        }

        try {
            handler.handle(job.getPayload());
            scheduledJobService.succeed(job.getId(), workerId);
        } catch (Exception failure) {
            log.warn("Scheduled job {} ({}) failed", job.getId(), job.getJobType(), failure);
            scheduledJobService.fail(job.getId(), workerId, failure);
        }
    }
}

