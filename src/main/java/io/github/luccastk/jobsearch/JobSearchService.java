package io.github.luccastk.jobsearch;

import io.github.luccastk.jobsearch.linkedin.JobCardParser;
import io.github.luccastk.jobsearch.linkedin.LinkedInGuestClient;
import io.github.luccastk.jobsearch.linkedin.LinkedInProperties;
import io.github.luccastk.jobsearch.linkedin.UpstreamException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Pages through LinkedIn's guest search until enough unique jobs are collected. */
@Service
public class JobSearchService {

    static final int MAX_PAGES = 10;

    private final LinkedInGuestClient client;
    private final JobCardParser parser;
    private final LinkedInProperties properties;

    public JobSearchService(LinkedInGuestClient client, JobCardParser parser, LinkedInProperties properties) {
        this.client = client;
        this.parser = parser;
        this.properties = properties;
    }

    /**
     * Fails with {@link UpstreamException} only when the first page fails; a later failure stops
     * pagination and marks the result partial.
     */
    public SearchResponse search(SearchQuery query) {
        Map<String, JobPosting> jobsById = new LinkedHashMap<>();
        boolean partial = false;
        int start = 0;
        for (int page = 0; page < MAX_PAGES && jobsById.size() < query.maxResults(); page++) {
            if (page > 0) {
                pause();
            }
            JobCardParser.Page parsed;
            try {
                parsed = parser.parse(client.fetchPage(query, start));
            } catch (UpstreamException e) {
                if (page == 0) {
                    throw e;
                }
                partial = true;
                break;
            }
            if (parsed.cardCount() == 0) {
                break;
            }
            parsed.jobs().forEach(job -> jobsById.putIfAbsent(job.id(), job));
            start += parsed.cardCount();
        }
        List<JobPosting> jobs = jobsById.values().stream().limit(query.maxResults()).toList();
        return new SearchResponse(jobs.size(), partial, jobs);
    }

    private void pause() {
        try {
            Thread.sleep(properties.pageDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchInterruptedException("search interrupted while waiting between LinkedIn page requests", e);
        }
    }
}
