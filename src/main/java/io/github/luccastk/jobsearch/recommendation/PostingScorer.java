package io.github.luccastk.jobsearch.recommendation;

import io.github.luccastk.jobsearch.linkedin.JobDetail;
import java.util.List;
import org.springframework.stereotype.Component;

/** Scores one posting against the profile from its title and, when readable, its detail page. */
@Component
public class PostingScorer {

    /** A posting's score; skill lists are empty and the category {@code UNRATED} without a description. */
    public record Score(Seniority seniority, boolean descriptionAvailable, SkillMatch skills, Category category) {
    }

    private static final SkillMatch NOTHING_DETECTED = new SkillMatch(List.of(), List.of());

    private final SkillMatcher matcher;

    public PostingScorer(SkillMatcher matcher) {
        this.matcher = matcher;
    }

    /** @param detail the parsed detail page, or {@code null} when it could not be fetched */
    public Score score(String title, JobDetail detail) {
        if (detail == null || detail.description() == null) {
            return new Score(SeniorityRules.classify(title, null), false, NOTHING_DETECTED, Category.UNRATED);
        }
        SkillMatch skills = matcher.match(title + "\n" + detail.description());
        return new Score(SeniorityRules.classify(title, detail.seniorityLevel()), true, skills, Category.of(skills));
    }
}
