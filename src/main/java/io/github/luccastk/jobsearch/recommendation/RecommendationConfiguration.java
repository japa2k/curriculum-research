package io.github.luccastk.jobsearch.recommendation;

import io.github.luccastk.jobsearch.profile.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RecommendationConfiguration {

    @Bean
    SkillMatcher skillMatcher(Profile profile) {
        return new SkillMatcher(profile.skillDictionary(), profile.knownSkills());
    }
}
