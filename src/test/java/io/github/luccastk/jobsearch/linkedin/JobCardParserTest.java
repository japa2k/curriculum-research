package io.github.luccastk.jobsearch.linkedin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luccastk.jobsearch.JobPosting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JobCardParserTest {

    private final JobCardParser parser = new JobCardParser();

    @Test
    void parsesEveryCardOfARealGuestResponse() throws IOException {
        JobCardParser.Page page = parser.parse(fixture("search-page-java-brazil.html"));

        assertThat(page.cardCount()).isEqualTo(10);
        assertThat(page.jobs()).hasSize(10);
        assertThat(page.jobs().getFirst()).isEqualTo(new JobPosting(
                "4459064563",
                "Java & Kotlin Developer – Spring Framework - Remote Work | REF#294652",
                "BairesDev",
                "São Paulo, São Paulo, Brazil",
                "https://br.linkedin.com/jobs/view/java-kotlin-developer-%E2%80%93-spring-framework-remote-work-ref%23294652-at-bairesdev-4459064563",
                LocalDate.of(2026, 9, 8)));
    }

    @Test
    void readsTheDateOfCardsMarkedNew() throws IOException {
        JobCardParser.Page page = parser.parse(fixture("search-page-java-brazil.html"));

        assertThat(page.jobs().get(3).postedAt()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void skipsCardsWithoutIdOrTitleAndNullsMissingFields() throws IOException {
        JobCardParser.Page page = parser.parse(fixture("search-page-edge-cases.html"));

        assertThat(page.cardCount()).isEqualTo(4);
        assertThat(page.jobs()).containsExactly(
                new JobPosting("1001", "No company or location", null, null,
                        "https://www.linkedin.com/jobs/view/no-company-1001", null),
                new JobPosting("1003", "No date", "Acme", "Remote",
                        "https://www.linkedin.com/jobs/view/no-date-1003", null));
    }

    @Test
    void returnsNoCardsForAnEmptyResponse() {
        JobCardParser.Page page = parser.parse("");

        assertThat(page.cardCount()).isZero();
        assertThat(page.jobs()).isEmpty();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = JobCardParserTest.class.getResourceAsStream("/linkedin/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
