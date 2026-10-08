package io.github.luccastk.jobsearch.recommendation;

import java.util.List;

/** {@code partial} is true when a search or a detail request failed, or a detail page had no description. */
public record RecommendationsResponse(int count, boolean partial, List<RecommendedJob> jobs) {
}
