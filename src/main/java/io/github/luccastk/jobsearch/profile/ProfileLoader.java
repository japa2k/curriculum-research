package io.github.luccastk.jobsearch.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads and validates {@code profile.yml}; any problem is an {@link InvalidProfileException}. */
public final class ProfileLoader {

    /** Each pair is one LinkedIn search request per recommendations call. */
    public static final int MAX_SEARCH_LOCATION_PAIRS = 24;

    // Default Jackson settings reject unknown fields, so a misspelled key fails instead of being ignored.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProfileLoader() {
    }

    public static Profile load(Resource resource) {
        if (!resource.exists()) {
            throw new InvalidProfileException("profile file not found: " + resource.getDescription());
        }
        Object yaml;
        try (InputStream in = resource.getInputStream()) {
            yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException | RuntimeException e) {
            throw new InvalidProfileException("profile could not be read: " + e.getMessage(), e);
        }
        if (yaml == null) {
            throw new InvalidProfileException("profile is empty: " + resource.getDescription());
        }
        Profile profile;
        try {
            profile = MAPPER.convertValue(yaml, Profile.class);
        } catch (IllegalArgumentException e) {
            throw new InvalidProfileException("profile is invalid: " + e.getMessage(), e);
        }
        validate(profile);
        return profile;
    }

    private static void validate(Profile profile) {
        List<Profile.Search> searches = orEmpty(profile.searches());
        List<Profile.Location> locations = orEmpty(profile.locations());
        if (searches.isEmpty()) {
            throw new InvalidProfileException("profile searches must not be empty");
        }
        if (locations.isEmpty()) {
            throw new InvalidProfileException("profile locations must not be empty");
        }
        for (int i = 0; i < searches.size(); i++) {
            Profile.Search search = searches.get(i);
            requireText(search.keywords(), "searches[" + i + "].keywords");
            requireText(search.track(), "searches[" + i + "].track");
        }
        for (int i = 0; i < locations.size(); i++) {
            requireText(locations.get(i).location(), "locations[" + i + "].location");
        }
        int pairs = searches.size() * locations.size();
        if (pairs > MAX_SEARCH_LOCATION_PAIRS) {
            throw new InvalidProfileException("profile searches × locations is " + pairs
                    + "; at most " + MAX_SEARCH_LOCATION_PAIRS + " are allowed");
        }
        Set<String> dictionary = new HashSet<>();
        for (SkillDefinition skill : orEmpty(profile.skillDictionary())) {
            requireText(skill.name(), "skillDictionary name");
            if (!dictionary.add(skill.name())) {
                throw new InvalidProfileException("profile skillDictionary has a duplicate name '" + skill.name() + "'");
            }
            // A blank alias would compile to an empty alternative that matches every posting.
            for (String alias : skill.aliases()) {
                requireText(alias, "skillDictionary '" + skill.name() + "' aliases entry");
            }
        }
        for (String skill : orEmpty(profile.knownSkills())) {
            if (!dictionary.contains(skill)) {
                throw new InvalidProfileException("profile knownSkills entry '" + skill + "' is not in skillDictionary");
            }
        }
        Profile.StudyPlanSettings studyPlan = profile.studyPlan();
        if (studyPlan == null) {
            throw new InvalidProfileException("profile studyPlan is required");
        }
        if (studyPlan.weeklyHours() <= 0) {
            throw new InvalidProfileException("profile studyPlan.weeklyHours must be positive");
        }
        if (studyPlan.maxWeeks() <= 0) {
            throw new InvalidProfileException("profile studyPlan.maxWeeks must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileException("profile " + name + " must not be blank");
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
