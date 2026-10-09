package io.github.luccastk.curriculumresearch.domain.job;

/** Where a posting's full description is read from. Throws when it cannot be read or is empty. */
public interface JobDescriptionSource {

    String fetchDescription(JobPosting job);
}
