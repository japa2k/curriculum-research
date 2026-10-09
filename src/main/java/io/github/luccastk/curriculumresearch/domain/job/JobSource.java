package io.github.luccastk.curriculumresearch.domain.job;

import java.util.List;

/** Where job postings come from, one page of search results at a time. */
public interface JobSource {

    List<JobPosting> searchJobs(String keywords, String location, int start);
}
