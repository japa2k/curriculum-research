package io.github.luccastk.jobsearch.linkedin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the guest jobs endpoint, bound from {@code linkedin.*} in {@code application.yml}.
 *
 * @param pageDelay minimum wait between consecutive page requests
 * @param timeout   connect and read timeout of each page request
 */
@ConfigurationProperties("linkedin")
public record LinkedInProperties(String baseUrl, String userAgent, Duration pageDelay, Duration timeout) {
}
