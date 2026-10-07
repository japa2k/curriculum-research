package io.github.luccastk.jobsearch.alerts;

import io.github.luccastk.jobsearch.JobSearchService;
import io.github.luccastk.jobsearch.linkedin.LinkedInProperties;
import io.github.luccastk.jobsearch.telegram.TelegramClient;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** Wires the hourly Telegram alerts; with {@code alerts.enabled=false} none of this exists. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "alerts.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
class AlertsConfiguration {

    /** The store and the cycle depend on it, so bad settings fail before the database file is created. */
    @Bean
    AlertSettings alertSettings(AlertsProperties alerts, TelegramProperties telegram) {
        return AlertSettings.from(alerts, telegram);
    }

    @Bean
    SeenJobStore seenJobStore(AlertSettings settings) {
        return new SeenJobStore(settings.dbPath());
    }

    @Bean
    TelegramClient telegramClient(RestClient.Builder builder, TelegramProperties telegram) {
        return new TelegramClient(builder, telegram);
    }

    @Bean
    AlertCycle alertCycle(AlertSettings settings, JobSearchService service, SeenJobStore store,
            TelegramClient telegramClient, LinkedInProperties linkedIn) {
        return new AlertCycle(settings.searches(), service, store, telegramClient, linkedIn.pageDelay());
    }

    @Bean
    AlertScheduler alertScheduler(AlertCycle cycle, AlertsProperties alerts) {
        return new AlertScheduler(cycle, alerts.interval());
    }
}
