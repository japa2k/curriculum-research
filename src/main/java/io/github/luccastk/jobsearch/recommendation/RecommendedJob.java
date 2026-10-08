package io.github.luccastk.jobsearch.recommendation;

import java.time.LocalDate;
import java.util.List;

/**
 * A posting scored against the profile. The first six fields mean what they mean in {@code /api/jobs/search}.
 *
 * @param track the profile track of the first search that found this posting
 */
public record RecommendedJob(
        String id,
        String title,
        String company,
        String location,
        String url,
        LocalDate postedAt,
        String track,
        Seniority seniority,
        boolean descriptionAvailable,
        List<String> matchedSkills,
        List<String> missingSkills,
        Category category) {
}
