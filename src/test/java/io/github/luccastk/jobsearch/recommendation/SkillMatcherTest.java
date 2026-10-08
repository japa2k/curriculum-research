package io.github.luccastk.jobsearch.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luccastk.jobsearch.profile.SkillDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SkillMatcherTest {

    private static final List<SkillDefinition> DICTIONARY = List.of(
            skill("Java"),
            skill("JavaScript"),
            skill("C#"),
            skill("C++"),
            skill(".NET"),
            skill("Node.js", "Node", "NodeJS"),
            skill("NestJS", "Nest.js"),
            skill("Kubernetes", "k8s"),
            skill("AWS", "Amazon Web Services"),
            new SkillDefinition("Go", List.of("Golang"), true));

    private final SkillMatcher matcher = new SkillMatcher(DICTIONARY, Set.of("Java", "Node.js", "NestJS"));

    @Test
    void splitsDetectedSkillsIntoMatchedAndMissing() {
        SkillMatch match = matcher.match("We use Java, Node.js and Kubernetes on AWS.");

        assertThat(match.matchedSkills()).containsExactly("Java", "Node.js");
        assertThat(match.missingSkills()).containsExactly("AWS", "Kubernetes");
    }

    @Test
    void detectsSkillsThroughAliasesCaseInsensitively() {
        SkillMatch match = matcher.match("experience with nest.js, K8S and amazon web services");

        assertThat(match.matchedSkills()).containsExactly("NestJS");
        assertThat(match.missingSkills()).containsExactly("AWS", "Kubernetes");
    }

    @Test
    void reportsEachSkillOnceSortedAlphabetically() {
        SkillMatch match = matcher.match("Kubernetes, AWS, k8s, aws, Java, java, Node, NodeJS");

        assertThat(match.matchedSkills()).containsExactly("Java", "Node.js");
        assertThat(match.missingSkills()).containsExactly("AWS", "Kubernetes");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Strong JavaScript skills             | JavaScript",
            "C# and .NET developer                | .NET, C#",
            "Modern C++ (C++20)                   | C++",
            "Java/Kotlin backend                  | Java",
            "Java.                                | Java",
            "(Java)                               | Java",
    })
    void respectsWholeWordBoundaries(String text, String expected) {
        SkillMatch match = new SkillMatcher(DICTIONARY, Set.of()).match(text);

        assertThat(match.missingSkills()).containsExactly(expected.split(",\\s*"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Javanese culture",
            "ASP.NETCore",
            "javascripting",
            "Node.jsx",
            "C#m",
    })
    void doesNotMatchInsideLongerWords(String text) {
        SkillMatch match = new SkillMatcher(List.of(skill("Java"), skill(".NET"), skill("Node.js"), skill("C#")),
                Set.of()).match(text);

        assertThat(match.missingSkills()).isEmpty();
    }

    @Test
    void honorsCaseSensitiveSkills() {
        SkillMatcher goOnly = new SkillMatcher(List.of(new SkillDefinition("Go", List.of("Golang"), true)), Set.of());

        assertThat(goOnly.match("let's go build it").missingSkills()).isEmpty();
        assertThat(goOnly.match("Services in Go and Rust").missingSkills()).containsExactly("Go");
        assertThat(goOnly.match("Golang microservices").missingSkills()).containsExactly("Go");
        assertThat(goOnly.match("golang microservices").missingSkills()).isEmpty();
    }

    @Test
    void returnsEmptyListsForTextWithoutSkills() {
        SkillMatch match = matcher.match("We value curiosity.");

        assertThat(match.matchedSkills()).isEmpty();
        assertThat(match.missingSkills()).isEmpty();
    }

    private static SkillDefinition skill(String name, String... aliases) {
        return new SkillDefinition(name, List.of(aliases), false);
    }
}
