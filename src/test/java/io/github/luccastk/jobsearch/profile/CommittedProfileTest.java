package io.github.luccastk.jobsearch.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** The {@code profile.yml} shipped with the app, seeded from the engineer's résumé. */
class CommittedProfileTest {

    private final ClassPathResource resource = new ClassPathResource("profile.yml");
    private final Profile profile = ProfileLoader.load(resource);

    @Test
    void coversTheResumeCoreRoles() {
        List<String> coreKeywords = profile.searches().stream()
                .filter(search -> search.track().equals("CORE"))
                .map(search -> search.keywords().toLowerCase())
                .toList();

        assertThat(coreKeywords).anyMatch(keywords -> keywords.contains("fullstack") || keywords.contains("full stack"));
        assertThat(coreKeywords).anyMatch(keywords -> keywords.contains("java") || keywords.contains("spring"));
    }

    @Test
    void searchesEveryAdjacentTrack() {
        assertThat(profile.searches()).extracting(Profile.Search::track).contains(
                "BACKEND_JVM", "PLATFORM_DEVOPS", "APPSEC_DEVSECOPS", "AI_ENGINEER", "MOBILE_REACT_NATIVE");
    }

    @Test
    void coversRemoteBrazilOnSiteSaoPauloAndRemoteInternational() {
        assertThat(profile.locations()).anyMatch(l -> l.remote() && l.location().contains("Brazil"));
        assertThat(profile.locations()).anyMatch(l -> !l.remote() && l.location().contains("São Paulo"));
        assertThat(profile.locations()).anyMatch(l -> l.remote() && !l.location().contains("Brazil"));
    }

    @Test
    void budgetsTenHoursAWeekForAtMostEightWeeks() {
        assertThat(profile.studyPlan()).isEqualTo(new Profile.StudyPlanSettings(10, 8));
    }

    @Test
    void knowsTheResumeSkills() {
        assertThat(profile.knownSkills()).contains(
                "React", "Next.js", "TypeScript", "Node.js", "NestJS", "Express", "Java", "Spring Boot",
                "Python", "Django", "PostgreSQL", "Prisma", "Kafka", "Redis", "Docker", "GitHub Actions", "Jest",
                "JUnit", "JWT", "OAuth", "RBAC", "Cloudflare Workers");
    }

    @Test
    void canDetectTheSkillsAdjacentTracksRequire() {
        assertThat(profile.skillDictionary()).extracting(SkillDefinition::name).contains(
                "Kotlin", "Go", "AWS", "Kubernetes", "Terraform", "GraphQL", "React Native", "OWASP", "SAST",
                "DAST", "LangChain", "RAG", "Vector databases");
        assertThat(profile.knownSkills()).doesNotContain("AWS", "Kubernetes", "Terraform", "Kotlin");
    }

    @Test
    void containsNoPersonalContactData() throws IOException {
        String raw;
        try (InputStream in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(raw).doesNotContainPattern(Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"));
        assertThat(raw).doesNotContainPattern(Pattern.compile("\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}"));
        assertThat(raw.toLowerCase()).doesNotContain("rua ", "avenida", "av. ", "linkedin.com/in");
    }
}
