package io.github.luccastk.jobsearch.profile;

import java.util.List;

/**
 * A skill the matcher can detect in a posting.
 *
 * @param aliases       other spellings that count as this skill (the name itself always does)
 * @param caseSensitive when true, the name and aliases only match with the exact casing
 */
public record SkillDefinition(String name, List<String> aliases, boolean caseSensitive) {

    public SkillDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
