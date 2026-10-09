package io.github.luccastk.curriculumresearch.infra.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.luccastk.curriculumresearch.application.job.JobDelivery;

/** Drips the stored jobs into the chat on a schedule, a small batch at a time. */
@Component
public class JobDeliveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobDeliveryScheduler.class);

    private final JobDelivery delivery;
    private final int batchSize;

    public JobDeliveryScheduler(JobDelivery delivery, @Value("${jobs.delivery.batch-size}") int batchSize) {
        this.delivery = delivery;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${jobs.delivery.cron}")
    public void deliver() {
        int sent = delivery.deliverNext(batchSize);
        if (sent > 0) {
            log.info("Job delivery: {} jobs sent", sent);
        }
    }
}
