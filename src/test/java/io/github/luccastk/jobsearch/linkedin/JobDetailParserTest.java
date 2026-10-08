package io.github.luccastk.jobsearch.linkedin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JobDetailParserTest {

    private final JobDetailParser parser = new JobDetailParser();

    @Test
    void parsesARealGuestDetailResponse() throws IOException {
        JobDetail detail = parser.parse(fixture("detail-page-fullstack-node.html"));

        assertThat(detail.title()).isEqualTo("Fullstack Node + Nest + React Developer | REF#305530");
        assertThat(detail.company()).isEqualTo("BairesDev");
        assertThat(detail.seniorityLevel()).isEqualTo("Not Applicable");
        assertThat(detail.description())
                .startsWith("At BairesDev®, we've been leading the way")
                .contains("Experience using the Nest.js framework.")
                .endsWith("Join a global team where your unique talents can truly thrive!");
    }

    @Test
    void readsOnlyTheSeniorityLevelCriterion() {
        JobDetail detail = parser.parse("""
                <h2 class="top-card-layout__title">  Dev  </h2>
                <div class="show-more-less-html__markup"><p>Java</p></div>
                <ul>
                  <li class="description__job-criteria-item">
                    <h3 class="description__job-criteria-subheader">Employment type</h3>
                    <span class="description__job-criteria-text">Full-time</span>
                  </li>
                  <li class="description__job-criteria-item">
                    <h3 class="description__job-criteria-subheader"> seniority LEVEL </h3>
                    <span class="description__job-criteria-text"> Entry level </span>
                  </li>
                </ul>""");

        assertThat(detail).isEqualTo(new JobDetail("Dev", null, "Java", "Entry level"));
    }

    @Test
    void returnsNullsForMissingOrEmptyElements() {
        JobDetail detail = parser.parse("""
                <h2 class="top-card-layout__title"></h2>
                <div class="show-more-less-html__markup">   </div>
                <li class="description__job-criteria-item">
                  <h3 class="description__job-criteria-subheader">Seniority level</h3>
                </li>""");

        assertThat(detail).isEqualTo(new JobDetail(null, null, null, null));
        assertThat(parser.parse("")).isEqualTo(new JobDetail(null, null, null, null));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = JobDetailParserTest.class.getResourceAsStream("/linkedin/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
