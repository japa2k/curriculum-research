package io.github.luccastk.curriculumresearch.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.luccastk.curriculumresearch.domain.job.JobFilter;

/** Registers the domain services as beans, so the domain itself stays free of Spring. */
@Configuration
public class DomainConfig {

    @Bean
    public JobFilter jobFilter() {
        return new JobFilter();
    }
}
