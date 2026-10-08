package io.github.luccastk.jobsearch.recommendation;

import io.github.luccastk.jobsearch.profile.SkillDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Detects dictionary skills in posting text and splits them by whether the profile knows them. */
public class SkillMatcher {

    static final Comparator<String> ALPHABETICAL =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    /**
     * A term must not touch a letter or digit, nor a {@code .}, {@code +} or {@code #} that would make
     * it part of a longer name ({@code ASP.NET}, {@code C++}, {@code C#}). A trailing {@code .} still
     * ends the term unless a letter or digit follows it, so "Java." matches but "Node.jsx" does not.
     */
    private static final String BEFORE = "(?<![\\p{L}\\p{N}.+#])";
    private static final String AFTER = "(?![\\p{L}\\p{N}+#])(?!\\.[\\p{L}\\p{N}])";

    private record CompiledSkill(String name, Pattern pattern) {
    }

    private final List<CompiledSkill> skills;
    private final Set<String> knownSkills;

    public SkillMatcher(List<SkillDefinition> dictionary, Collection<String> knownSkills) {
        this.skills = dictionary.stream().map(SkillMatcher::compile).toList();
        this.knownSkills = Set.copyOf(knownSkills);
    }

    public SkillMatch match(String text) {
        Set<String> matched = new TreeSet<>(ALPHABETICAL);
        Set<String> missing = new TreeSet<>(ALPHABETICAL);
        for (CompiledSkill skill : skills) {
            if (skill.pattern().matcher(text).find()) {
                (knownSkills.contains(skill.name()) ? matched : missing).add(skill.name());
            }
        }
        return new SkillMatch(new ArrayList<>(matched), new ArrayList<>(missing));
    }

    private static CompiledSkill compile(SkillDefinition skill) {
        String terms = Stream.concat(Stream.of(skill.name()), skill.aliases().stream())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        int flags = skill.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return new CompiledSkill(skill.name(), Pattern.compile(BEFORE + "(?:" + terms + ")" + AFTER, flags));
    }
}
