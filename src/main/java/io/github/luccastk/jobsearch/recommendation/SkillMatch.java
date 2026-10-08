package io.github.luccastk.jobsearch.recommendation;

import java.util.List;

/** Dictionary skills found in a posting, split by whether the profile knows them; each sorted, no duplicates. */
public record SkillMatch(List<String> matchedSkills, List<String> missingSkills) {
}
