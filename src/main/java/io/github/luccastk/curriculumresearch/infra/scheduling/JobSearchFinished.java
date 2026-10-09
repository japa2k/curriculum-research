package io.github.luccastk.curriculumresearch.infra.scheduling;

import java.util.List;

import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

/**
 * Published when a search run ends. {@code requested} tells a run asked for in the chat apart
 * from a scheduled one, so the chat can be told when nothing new turned up.
 */
public record JobSearchFinished(List<JobPosting> newJobs, boolean requested) {
}
