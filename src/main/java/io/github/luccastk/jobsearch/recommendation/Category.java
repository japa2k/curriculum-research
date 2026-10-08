package io.github.luccastk.jobsearch.recommendation;

/** How far a posting is from the profile; declaration order is the ranking order. */
public enum Category {
    /** No detected skill is missing. */
    MATCH,
    /** 1 to 3 detected skills are missing. */
    STUDYABLE,
    /** More than 3 detected skills are missing. */
    STRETCH,
    /** The description could not be read, so nothing was scored. */
    UNRATED;

    static final int MAX_STUDYABLE_MISSING = 3;

    static Category of(SkillMatch match) {
        int missing = match.missingSkills().size();
        if (missing == 0) {
            return MATCH;
        }
        return missing <= MAX_STUDYABLE_MISSING ? STUDYABLE : STRETCH;
    }
}
