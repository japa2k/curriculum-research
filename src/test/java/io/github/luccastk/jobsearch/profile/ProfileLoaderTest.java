package io.github.luccastk.jobsearch.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class ProfileLoaderTest {

    private static final String VALID = """
            knownSkills: [Java]
            skillDictionary:
              - name: Java
              - name: Go
                aliases: [Golang]
                caseSensitive: true
            searches:
              - keywords: java developer
                track: CORE
            locations:
              - location: Brazil
                remote: true
            studyPlan:
              weeklyHours: 10
              maxWeeks: 8
            """;

    @Test
    void loadsAValidProfile() {
        Profile profile = ProfileLoader.load(yaml(VALID));

        assertThat(profile).isEqualTo(new Profile(
                List.of("Java"),
                List.of(new SkillDefinition("Java", List.of(), false),
                        new SkillDefinition("Go", List.of("Golang"), true)),
                List.of(new Profile.Search("java developer", "CORE")),
                List.of(new Profile.Location("Brazil", true)),
                new Profile.StudyPlanSettings(10, 8)));
    }

    @Test
    void failsWhenTheFileIsMissing() {
        assertThatThrownBy(() -> ProfileLoader.load(new ClassPathResource("no-such-profile.yml")))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void failsWhenTheFileIsNotYaml() {
        assertThatThrownBy(() -> ProfileLoader.load(yaml("searches: [unclosed")))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("profile");
    }

    @Test
    void failsOnUnknownFields() {
        assertThatThrownBy(() -> ProfileLoader.load(yaml(VALID + "knownSkils: [Java]\n")))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("knownSkils");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "searches:\\n  - keywords: java developer\\n    track: CORE  | searches: []                       | searches",
            "locations:\\n  - location: Brazil\\n    remote: true     | locations: []                      | locations",
            "knownSkills: [Java]                                       | knownSkills: [Java, Rust]          | Rust",
            "weeklyHours: 10                                           | weeklyHours: 0                     | weeklyHours",
            "maxWeeks: 8                                               | maxWeeks: -1                       | maxWeeks",
            "track: CORE                                               | 'track: \" \"'                     | track",
            "keywords: java developer                                  | 'keywords: \"\"'                   | keywords",
            "location: Brazil                                          | 'location: \"\"'                   | location",
    })
    void failsNamingTheInvalidPart(String original, String replacement, String named) {
        String yaml = VALID.replace(original.replace("\\n", "\n"), replacement.replace("\\n", "\n"));
        assertThat(yaml).isNotEqualTo(VALID);

        assertThatThrownBy(() -> ProfileLoader.load(yaml(yaml)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining(named);
    }

    @Test
    void failsWithoutStudyPlanSettings() {
        String yaml = VALID.substring(0, VALID.indexOf("studyPlan:"));

        assertThatThrownBy(() -> ProfileLoader.load(yaml(yaml)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("studyPlan");
    }

    @Test
    void acceptsUpToTwentyFourSearchLocationPairs() {
        assertThat(ProfileLoader.load(yaml(withPairs(8, 3))).searches()).hasSize(8);
        assertThat(ProfileLoader.load(yaml(withPairs(6, 4))).locations()).hasSize(4);
    }

    @Test
    void failsAboveTwentyFourSearchLocationPairs() {
        assertThatThrownBy(() -> ProfileLoader.load(yaml(withPairs(5, 5))))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("25")
                .hasMessageContaining("24");
    }

    private static String withPairs(int searches, int locations) {
        StringBuilder yaml = new StringBuilder("knownSkills: []\nskillDictionary: []\nsearches:\n");
        for (int i = 0; i < searches; i++) {
            yaml.append("  - keywords: search ").append(i).append("\n    track: CORE\n");
        }
        yaml.append("locations:\n");
        for (int i = 0; i < locations; i++) {
            yaml.append("  - location: place ").append(i).append("\n    remote: false\n");
        }
        return yaml.append("studyPlan:\n  weeklyHours: 10\n  maxWeeks: 8\n").toString();
    }

    private static ByteArrayResource yaml(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test profile");
    }
}
