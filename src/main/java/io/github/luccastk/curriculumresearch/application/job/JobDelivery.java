package io.github.luccastk.curriculumresearch.application.job;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.job.JobNotifier;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobRepository;

/**
 * Sends the stored jobs not sent yet, a few at a time, oldest found first, each with its tailored
 * résumé and study plan. A job whose kit could not be made still goes out, with a notice instead.
 */
@Service
public class JobDelivery {
    private static final Logger log = LoggerFactory.getLogger(JobDelivery.class);

    private final JobRepository jobs;
    private final ApplicationPreparation preparation;
    private final JobNotifier notifier;

    public JobDelivery(JobRepository jobs, ApplicationPreparation preparation, JobNotifier notifier) {
        this.jobs = jobs;
        this.preparation = preparation;
        this.notifier = notifier;
    }

    /** Sends up to {@code batchSize} unsent jobs and returns how many went out. */
    public int deliverNext(int batchSize) {
        int sent = 0;
        for (JobPosting job : jobs.findUnsent(batchSize)) {
            Optional<ApplicationKit> kit = prepare(job);
            try {
                notifier.send(job, kit);
                jobs.markSent(job);
                sent++;
            } catch (RuntimeException e) {
                // Left unsent, so the next delivery tries it again.
                log.warn("Failed to send job {}: {}", job.url(), e.getMessage());
            }
        }
        return sent;
    }

    private Optional<ApplicationKit> prepare(JobPosting job) {
        try {
            return Optional.of(preparation.prepare(job));
        } catch (RuntimeException e) {
            log.warn("Failed to prepare the application kit for job {}: {}", job.url(), e.getMessage());
            return Optional.empty();
        }
    }
}
