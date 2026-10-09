package io.github.luccastk.curriculumresearch.application.job;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.github.luccastk.curriculumresearch.domain.job.JobArea;
import io.github.luccastk.curriculumresearch.domain.job.JobFilter;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobRepository;
import io.github.luccastk.curriculumresearch.domain.job.JobSource;

/** Searches a job source, keeps the jobs in the wanted areas and stores them, returning only the new ones. */
@Service
public class JobSearch {
    private final JobSource source;
    private final JobFilter filter;
    private final JobRepository jobs;

    public JobSearch(JobSource source, JobFilter filter, JobRepository jobs) {
        this.source = source;
        this.filter = filter;
        this.jobs = jobs;
    }

    public List<JobPosting> search(String keywords, String location, int start, Set<JobArea> wanted) {
        return filter.filter(source.searchJobs(keywords, location, start), wanted).stream()
                .filter(jobs::save)
                .toList();
    }
}
