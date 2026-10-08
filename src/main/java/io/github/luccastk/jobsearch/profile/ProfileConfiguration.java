package io.github.luccastk.jobsearch.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** Loads the profile at startup, so a missing or invalid file stops the application. */
@Configuration(proxyBeanMethods = false)
class ProfileConfiguration {

    @Bean
    Profile profile(@Value("${profile.location}") Resource location) {
        return ProfileLoader.load(location);
    }
}
