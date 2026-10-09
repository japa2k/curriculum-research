package io.github.luccastk.curriculumresearch.domain.job;

import java.util.List;
import java.util.Optional;

/** Jobs found so far, keyed by URL so the same posting is stored only once. */
public interface JobRepository {

    /** Returns true when the job is new, false when its URL was already stored. */
    boolean save(JobPosting job);

    List<JobPosting> findAll();

    /** The jobs not sent yet, oldest found first. */
    List<JobPosting> findUnsent(int limit);

    void markSent(JobPosting job);

    /** Empty until {@link #saveDescription} stores the posting's description. */
    Optional<String> findDescription(JobPosting job);

    void saveDescription(JobPosting job, String description);
}
