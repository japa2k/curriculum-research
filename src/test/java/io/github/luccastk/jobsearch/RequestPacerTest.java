package io.github.luccastk.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.luccastk.jobsearch.linkedin.LinkedInProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestPacerTest {

    @Test
    void keepsTheSearchEndpointsInterruptMessageWhenInterrupted() {
        RequestPacer pacer = new RequestPacer(
                new LinkedInProperties("http://localhost", "agent", Duration.ofSeconds(10), Duration.ofSeconds(1)));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(pacer::pause)
                    .isInstanceOf(SearchInterruptedException.class)
                    .hasMessage("search interrupted while waiting between LinkedIn page requests");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag for the next test on this thread
        }
    }
}
