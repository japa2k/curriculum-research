package io.github.luccastk.jobsearch.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.luccastk.jobsearch.JobSearchApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;

class ProfileStartupTest {

    @Test
    void failsToStartWhenTheProfileIsMissing() {
        assertThatThrownBy(() -> start("classpath:no-such-profile.yml"))
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .isInstanceOf(InvalidProfileException.class)
                        .hasMessageContaining("not found"));
    }

    @Test
    void startsWithTheCommittedProfile() {
        try (ConfigurableApplicationContext context = start("classpath:profile.yml")) {
            assertThat(context.getBean(Profile.class).searches()).isNotEmpty();
        }
    }

    private static ConfigurableApplicationContext start(String profileLocation) {
        return new SpringApplicationBuilder(JobSearchApplication.class)
                .web(WebApplicationType.NONE)
                .run("--profile.location=" + profileLocation, "--alerts.enabled=false");
    }
}
