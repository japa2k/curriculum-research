package io.github.luccastk.jobsearch.studyplan;

import io.github.luccastk.jobsearch.recommendation.Seniority;
import java.util.List;

/** A generated study plan for one posting; {@code plan} is the CLI's Markdown, trimmed. */
public record StudyPlan(
        String jobId,
        String title,
        String company,
        String url,
        Seniority seniority,
        List<String> matchedSkills,
        List<String> missingSkills,
        int weeklyHours,
        int maxWeeks,
        String plan) {
}
