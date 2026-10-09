package io.github.luccastk.curriculumresearch.domain.application;

import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

/** Writes the study plan and the tailored résumé for a job. Throws when it cannot. */
public interface ApplicationWriter {

    ApplicationDocuments write(JobPosting job, String description);
}
