package io.github.luccastk.curriculumresearch.infra.scheduling;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.luccastk.curriculumresearch.application.job.JobSearch;
import io.github.luccastk.curriculumresearch.domain.job.JobArea;
import io.github.luccastk.curriculumresearch.domain.job.JobFilter;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

/**
 * Runs every search query on a schedule, or on demand, storing the backend, frontend and fullstack
 * jobs found and publishing the new ones in a {@link JobSearchFinished} event.
 */
@Component
public class JobSearchScheduler {
    private static final Logger log = LoggerFactory.getLogger(JobSearchScheduler.class);

    private final JobSearch search;
    private final JobFilter filter;
    private final String location;
    private final Duration pause;
    private final ApplicationEventPublisher events;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public JobSearchScheduler(JobSearch search, JobFilter filter,
            @Value("${jobs.search.location}") String location,
            @Value("${jobs.search.pause}") Duration pause,
            ApplicationEventPublisher events,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.search = search;
        this.filter = filter;
        this.location = location;
        this.pause = pause;
        this.events = events;
        this.executor = executor;
    }

    @Scheduled(cron = "${jobs.search.cron}")
    public void searchAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Scheduled job search skipped: a search is already running");
            return;
        }
        run(false);
    }

    /** Starts a search in the background; returns false when one is already running. */
    public boolean searchNow() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            executor.execute(() -> run(true));
        } catch (RejectedExecutionException e) {
            running.set(false);
            throw e;
        }
        return true;
    }

    private void run(boolean requested) {
        try {
            List<JobPosting> newJobs = searchQueries();
            log.info("Job search finished: {} new matching jobs found", newJobs.size());
            events.publishEvent(new JobSearchFinished(newJobs, requested));
        } finally {
            running.set(false);
        }
    }

    private List<JobPosting> searchQueries() {
        List<JobPosting> found = new ArrayList<>();
        for (String query : filter.searchQueries()) {
            try {
                found.addAll(search.search(query, location, 0, EnumSet.allOf(JobArea.class)));
            } catch (RuntimeException e) {
                // One failed query (timeout, rate limit) should not cost the rest of the run.
                log.warn("Job search for '{}' failed: {}", query, e.getMessage());
            }
            if (!sleep()) {
                break;
            }
        }
        return found;
    }

    private boolean sleep() {
        try {
            Thread.sleep(pause);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
