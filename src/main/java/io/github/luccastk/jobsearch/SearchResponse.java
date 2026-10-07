package io.github.luccastk.jobsearch;

import java.util.List;

/** {@code partial} is true when pagination stopped early because a later page request failed. */
public record SearchResponse(int count, boolean partial, List<JobPosting> jobs) {
}
