package io.github.luccastk.curriculumresearch.application.job;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.application.UsageLimitNotifier;
import io.github.luccastk.curriculumresearch.domain.application.UsageLimitReachedException;
import io.github.luccastk.curriculumresearch.domain.job.ApplyMethod;
import io.github.luccastk.curriculumresearch.domain.job.ApplyMethodSource;
import io.github.luccastk.curriculumresearch.domain.job.JobNotifier;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobRepository;

/**
 * Sends the stored jobs not sent yet, a few at a time, oldest found first, each with its tailored
 * résumé and study plan. Only Easy Apply jobs are sent: the others (applied to on the company's
 * site, or closed) are skipped for good before their kit is made, and a job whose apply method
 * cannot be read waits for the next delivery. A job whose kit could not be made still goes out, with a notice instead.
 * When the writer's usage limit is used up, the chat is told and the jobs wait unsent until it
 * resets (or, when the reset time is unknown, until a later delivery's kit is made), so they still
 * get their kits; the chat is told again when the writer is back.
 */
@Service
public class JobDelivery {
    private static final Logger log = LoggerFactory.getLogger(JobDelivery.class);
    // How many unsent jobs one delivery looks at per job it may send, so skipped ones leave room.
    private static final int CANDIDATES_PER_SLOT = 4;

    private final JobRepository jobs;
    private final ApplicationPreparation preparation;
    private final ApplyMethodSource applyMethods;
    private final JobNotifier notifier;
    private final UsageLimitNotifier limitNotifier;
    private final Clock clock;
    private boolean limited;
    // Null while the limit is not known to reset at a given time.
    private Instant resetsAt;

    public JobDelivery(JobRepository jobs, ApplicationPreparation preparation, ApplyMethodSource applyMethods,
            JobNotifier notifier, UsageLimitNotifier limitNotifier, Clock clock) {
        this.jobs = jobs;
        this.preparation = preparation;
        this.applyMethods = applyMethods;
        this.notifier = notifier;
        this.limitNotifier = limitNotifier;
        this.clock = clock;
    }

    /** Sends up to {@code batchSize} unsent jobs and returns how many went out. */
    public synchronized int deliverNext(int batchSize) {
        if (limited && resetsAt != null && clock.instant().isBefore(resetsAt)) {
            return 0;
        }
        List<JobPosting> unsent = jobs.findUnsent(batchSize * CANDIDATES_PER_SLOT);
        if (limited && resetsAt != null && unsent.isEmpty()) {
            // Nothing to try the writer on, but its reset time has passed.
            lifted();
        }
        int sent = 0;
        for (JobPosting job : unsent) {
            if (sent == batchSize) {
                break;
            }
            if (!isEasyApply(job)) {
                continue;
            }
            Optional<ApplicationKit> kit;
            try {
                kit = prepare(job);
            } catch (UsageLimitReachedException e) {
                // Left unsent, so it gets its kit once the limit resets.
                limitReached(e);
                break;
            }
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

    /** Skips the job for good when it is not Easy Apply; false too when that cannot be read. */
    private boolean isEasyApply(JobPosting job) {
        ApplyMethod apply;
        try {
            apply = applyMethods.applyMethod(job);
        } catch (RuntimeException e) {
            // Left unsent, so the next delivery reads it again.
            log.warn("Failed to read how to apply to job {}: {}", job.url(), e.getMessage());
            return false;
        }
        if (apply != ApplyMethod.EASY_APPLY) {
            log.info("Skipping job {}: not Easy Apply ({})", job.url(), apply);
            jobs.markSkipped(job, apply.name());
            return false;
        }
        return true;
    }

    private Optional<ApplicationKit> prepare(JobPosting job) {
        try {
            ApplicationKit kit = preparation.prepare(job);
            if (limited) {
                lifted();
            }
            return Optional.of(kit);
        } catch (UsageLimitReachedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to prepare the application kit for job {}: {}", job.url(), e.getMessage());
            return Optional.empty();
        }
    }

    private void limitReached(UsageLimitReachedException e) {
        log.warn("Job delivery paused: {}", e.getMessage());
        resetsAt = e.resetsAt().orElse(null);
        if (limited) {
            return;
        }
        limited = true;
        try {
            limitNotifier.limitReached(e.resetsAt());
        } catch (RuntimeException notifyError) {
            log.warn("Failed to announce the usage limit: {}", notifyError.getMessage());
        }
    }

    private void lifted() {
        log.info("Job delivery resumed: the usage limit is lifted");
        limited = false;
        resetsAt = null;
        try {
            limitNotifier.limitLifted();
        } catch (RuntimeException notifyError) {
            log.warn("Failed to announce the end of the usage limit: {}", notifyError.getMessage());
        }
    }
}
