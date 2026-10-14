package io.github.luccastk.curriculumresearch.domain.job;

/** Where a posting's apply method is read from. Throws when the posting cannot be read. */
public interface ApplyMethodSource {

    ApplyMethod applyMethod(JobPosting job);
}
