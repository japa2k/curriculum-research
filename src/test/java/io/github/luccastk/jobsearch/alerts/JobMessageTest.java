package io.github.luccastk.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luccastk.jobsearch.JobPosting;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JobMessageTest {

    @Test
    void listsTitleInBoldThenCompanyLocationDateAndUrl() {
        JobPosting job = new JobPosting("42", "Java Developer", "Acme", "São Paulo, Brazil",
                "https://www.linkedin.com/jobs/view/42", LocalDate.of(2026, 10, 1));

        assertThat(JobMessage.format(job)).isEqualTo("""
                <b>Java Developer</b>
                Acme
                São Paulo, Brazil
                Posted: 2026-10-01
                https://www.linkedin.com/jobs/view/42""");
    }

    @Test
    void omitsLinesWhoseFieldIsNull() {
        JobPosting job = new JobPosting("42", "Java Developer", null, null,
                "https://www.linkedin.com/jobs/view/42", null);

        assertThat(JobMessage.format(job)).isEqualTo("""
                <b>Java Developer</b>
                https://www.linkedin.com/jobs/view/42""");
    }

    @Test
    void escapesHtmlInTitleCompanyAndLocation() {
        JobPosting job = new JobPosting("42", "C++ <Senior> & R&D", "A&B <Labs>", "Rio > SP",
                "https://www.linkedin.com/jobs/view/42", null);

        assertThat(JobMessage.format(job)).isEqualTo("""
                <b>C++ &lt;Senior&gt; &amp; R&amp;D</b>
                A&amp;B &lt;Labs&gt;
                Rio &gt; SP
                https://www.linkedin.com/jobs/view/42""");
    }

    @Test
    void escapesAmpersandsInTheUrlSoTelegramCanParseTheMessage() {
        JobPosting job = new JobPosting("42", "Java Developer", null, null,
                "https://www.linkedin.com/jobs/view/42?refId=a&trackingId=b", null);

        assertThat(JobMessage.format(job)).endsWith("https://www.linkedin.com/jobs/view/42?refId=a&amp;trackingId=b");
    }
}
