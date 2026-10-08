package io.github.luccastk.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.luccastk.jobsearch.JobSearchApplication;
import io.github.luccastk.jobsearch.PostedWithin;
import io.github.luccastk.jobsearch.resume.ResumeProperties;
import io.github.luccastk.jobsearch.telegram.CallbackPoller;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts the real application (shipped {@code application.yml} included) with command-line overrides. */
@ExtendWith(OutputCaptureExtension.class)
class AlertsStartupTest {

    private static final String TOKEN = "123456:startup-test-token";

    @TempDir
    Path tempDir;

    @Test
    void failsAtStartupWhenTheBotTokenIsBlank() {
        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=", "--telegram.chat-id=1"))
                .satisfies(e -> assertThat(messages(e)).anyMatch(m -> m.contains("TELEGRAM_BOT_TOKEN")));
    }

    @Test
    void failsAtStartupWhenTheChatIdIsBlankWithoutLoggingTheToken(CapturedOutput output) {
        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=" + TOKEN, "--telegram.chat-id="))
                .satisfies(e -> assertThat(messages(e)).anyMatch(m -> m.contains("TELEGRAM_CHAT_ID")));

        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void failsAtStartupNamingAnInvalidSearch() {
        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=" + TOKEN, "--telegram.chat-id=1",
                "--alerts.searches[0].keywords=java", "--alerts.searches[0].maxResults=0"))
                .satisfies(e -> assertThat(messages(e))
                        .anyMatch(m -> m.contains("alerts.searches[0].maxResults")));
    }

    @Test
    void failsAtStartupNamingAnUnknownPostedWithin() {
        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=" + TOKEN, "--telegram.chat-id=1",
                "--alerts.searches[0].keywords=java", "--alerts.searches[0].postedWithin=YEAR"))
                .satisfies(e -> assertThat(messages(e)).anyMatch(m -> m.contains("alerts.searches[0].posted-within")));
    }

    @Test
    void failsAtStartupNamingTheBaseResumeWhenItIsMissing() {
        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=" + TOKEN, "--telegram.chat-id=1",
                "--resume.base-path=" + tempDir.resolve("no-such-resume.md")))
                .satisfies(e -> assertThat(messages(e)).anyMatch(m -> m.contains("resume.base-path")));
    }

    @Test
    void failsAtStartupNamingTheBaseResumeWhenItIsBlank() throws IOException {
        Path blank = Files.writeString(tempDir.resolve("resume-base.md"), "  \n\t\n");

        assertThatThrownBy(() -> runWithAlerts("--telegram.bot-token=" + TOKEN, "--telegram.chat-id=1",
                "--resume.base-path=" + blank))
                .satisfies(e -> assertThat(messages(e)).anyMatch(m -> m.contains("resume.base-path")));
    }

    @Test
    void neitherPollsTelegramNorRequiresTheBaseResumeWhenAlertsAreDisabled() {
        try (ConfigurableApplicationContext context = run("--alerts.enabled=false",
                "--resume.base-path=" + tempDir.resolve("no-such-resume.md"))) {
            assertThat(context.getBeansOfType(CallbackPoller.class)).isEmpty();
        }
    }

    @Test
    void shipsTheDocumentedResumeDefaults() {
        try (ConfigurableApplicationContext context = run("--alerts.enabled=false")) {
            ResumeProperties resume = context.getBean(ResumeProperties.class);

            assertThat(resume.basePath()).isEqualTo("./data/resume-base.md");
            assertThat(resume.applicationsDir()).isEqualTo("./data/applications");
        }
    }

    @Test
    void startsWithoutTelegramSettingsOrSchedulingWhenAlertsAreDisabled() {
        Path dbPath = tempDir.resolve("data/jobs.db");

        try (ConfigurableApplicationContext context = run("--alerts.enabled=false",
                "--telegram.bot-token=", "--telegram.chat-id=", "--alerts.db-path=" + dbPath)) {
            assertThat(context.getBeansOfType(AlertScheduler.class)).isEmpty();
            assertThat(context.getBeansOfType(AlertCycle.class)).isEmpty();
            assertThat(context.getBeansOfType(SeenJobStore.class)).isEmpty();
        }
        assertThat(dbPath.getParent()).doesNotExist();
    }

    @Test
    void shipsOneExampleSearchAndTheDocumentedDefaults() {
        try (ConfigurableApplicationContext context = run("--alerts.enabled=false")) {
            AlertsProperties alerts = context.getBean(AlertsProperties.class);
            TelegramProperties telegram = context.getBean(TelegramProperties.class);

            assertThat(alerts.interval()).isEqualTo(Duration.ofHours(1));
            assertThat(alerts.dbPath()).isEqualTo("./data/jobs.db");
            assertThat(alerts.searches()).containsExactly(
                    new AlertsProperties.Search("java", "Brazil", PostedWithin.DAY, "false", 25));
            assertThat(telegram.baseUrl()).isEqualTo("https://api.telegram.org");
            assertThat(telegram.timeout()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    /**
     * Surefire runs tests in {@code target/test-workdir} with {@code TELEGRAM_CHAT_ID} set in the
     * environment (see pom.xml), so this writes a throwaway {@code .env} there, never the real one.
     */
    @Test
    void readsTelegramSettingsFromDotEnvWithEnvironmentVariablesTakingPrecedence() throws IOException {
        Path dotEnv = Path.of(".env").toAbsolutePath();
        assumeTrue(dotEnv.getParent().endsWith("test-workdir"), "only runs in Surefire's test working directory");
        assumeTrue("chat-from-environment".equals(System.getenv("TELEGRAM_CHAT_ID")));
        Files.writeString(dotEnv, "TELEGRAM_BOT_TOKEN=token-from-dotenv\nTELEGRAM_CHAT_ID=chat-from-dotenv\n");

        try (ConfigurableApplicationContext context = run("--alerts.enabled=false")) {
            TelegramProperties telegram = context.getBean(TelegramProperties.class);

            assertThat(telegram.botToken()).isEqualTo("token-from-dotenv");
            assertThat(telegram.chatId()).isEqualTo("chat-from-environment");
        } finally {
            Files.delete(dotEnv);
        }
    }

    /** Alerts enabled, with unreachable upstreams so a context that wrongly starts can't reach real hosts. */
    private ConfigurableApplicationContext runWithAlerts(String... args) {
        List<String> allArgs = new ArrayList<>(List.of(
                "--alerts.enabled=true",
                "--alerts.db-path=" + tempDir.resolve("jobs.db"),
                "--telegram.base-url=http://localhost:1",
                "--linkedin.base-url=http://localhost:1"));
        allArgs.addAll(List.of(args));
        return run(allArgs.toArray(String[]::new));
    }

    private static ConfigurableApplicationContext run(String... args) {
        return new SpringApplicationBuilder(JobSearchApplication.class).web(WebApplicationType.NONE).run(args);
    }

    private static List<String> messages(Throwable e) {
        return Stream.iterate(e, t -> t != null, Throwable::getCause).map(String::valueOf).toList();
    }
}
