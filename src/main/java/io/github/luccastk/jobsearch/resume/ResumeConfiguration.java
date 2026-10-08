package io.github.luccastk.jobsearch.resume;

import io.github.luccastk.jobsearch.alerts.AlertsProperties;
import io.github.luccastk.jobsearch.profile.Profile;
import io.github.luccastk.jobsearch.studyplan.ClaudeCli;
import io.github.luccastk.jobsearch.studyplan.StudyPlanService;
import io.github.luccastk.jobsearch.telegram.CallbackPoller;
import io.github.luccastk.jobsearch.telegram.TelegramClient;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Wires the alert button: polling Telegram for presses and handling them. Like the alerts themselves, none of
 * this exists with {@code alerts.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "alerts.enabled", havingValue = "true", matchIfMissing = true)
class ResumeConfiguration {

    /** After {@code alertSettings}, so bad Telegram settings are reported first and before the database exists. */
    @Bean
    @DependsOn("alertSettings")
    ApplicationStore applicationStore(AlertsProperties alerts) {
        return new ApplicationStore(alerts.dbPath());
    }

    @Bean
    @DependsOn("alertSettings")
    ResumeTapHandler resumeTapHandler(ResumeProperties resume, TelegramClient telegramClient,
            StudyPlanService postings, ClaudeCli cli, Profile profile, ApplicationStore store) {
        return new ResumeTapHandler(telegramClient, postings, cli, profile, resume.readBaseResume(),
                new ApplicationFiles(Path.of(resume.applicationsDir())), store, Clock.systemDefaultZone());
    }

    @Bean
    CallbackPoller callbackPoller(TelegramClient telegramClient, TelegramProperties telegram,
            ResumeTapHandler handler) {
        return new CallbackPoller(telegramClient, telegram.chatId(), handler);
    }
}
