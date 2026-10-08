package io.github.luccastk.jobsearch.profile;

import java.util.List;

/**
 * The engineer's search profile, loaded from {@code profile.yml}.
 *
 * @param knownSkills     dictionary skill names the engineer already has
 * @param skillDictionary every skill the matcher can detect in a posting
 * @param searches        LinkedIn searches to run, each tagged with the track it explores
 * @param locations       where each search runs
 */
public record Profile(
        List<String> knownSkills,
        List<SkillDefinition> skillDictionary,
        List<Search> searches,
        List<Location> locations,
        StudyPlanSettings studyPlan) {

    public Profile {
        knownSkills = knownSkills == null ? List.of() : List.copyOf(knownSkills);
        skillDictionary = skillDictionary == null ? List.of() : List.copyOf(skillDictionary);
        searches = searches == null ? List.of() : List.copyOf(searches);
        locations = locations == null ? List.of() : List.copyOf(locations);
    }

    /** Track {@code CORE} is a role the résumé already covers; any other value names an adjacent track. */
    public record Search(String keywords, String track) {
    }

    public record Location(String location, boolean remote) {
    }

    public record StudyPlanSettings(int weeklyHours, int maxWeeks) {
    }
}
