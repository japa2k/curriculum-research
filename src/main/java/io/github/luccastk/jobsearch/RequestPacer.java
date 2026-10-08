package io.github.luccastk.jobsearch;

import io.github.luccastk.jobsearch.linkedin.LinkedInProperties;
import org.springframework.stereotype.Component;

/** Waits the configured delay between consecutive LinkedIn requests. */
@Component
public class RequestPacer {

    private final LinkedInProperties properties;

    public RequestPacer(LinkedInProperties properties) {
        this.properties = properties;
    }

    /** @throws SearchInterruptedException when the request thread is interrupted while waiting */
    public void pause() {
        try {
            Thread.sleep(properties.pageDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchInterruptedException("search interrupted while waiting between LinkedIn requests", e);
        }
    }
}
