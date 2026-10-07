package io.github.luccastk.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.luccastk.jobsearch.PostedWithin;
import io.github.luccastk.jobsearch.SearchQuery;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AlertSettingsTest {

    private static final String TOKEN = "123456:settings-test-token";
    private static final AlertsProperties.Search JAVA =
            new AlertsProperties.Search("java", "Brazil", PostedWithin.DAY, "false", 25);

    @Test
    void validatesEachSearchIntoAQuery() {
        AlertSettings settings = AlertSettings.from(alerts(List.of(
                JAVA, new AlertsProperties.Search("  kotlin  ", " ", PostedWithin.ANY, "TRUE", 10))), telegram(TOKEN, "1"));

        assertThat(settings.searches()).containsExactly(
                new SearchQuery("java", "Brazil", PostedWithin.DAY, false, 25),
                new SearchQuery("kotlin", null, PostedWithin.ANY, true, 10));
        assertThat(settings.dbPath()).isEqualTo("./data/jobs.db");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsABlankBotTokenNamingIt(String token) {
        assertThatThrownBy(() -> AlertSettings.from(alerts(List.of(JAVA)), telegram(token, "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_BOT_TOKEN");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsABlankChatIdNamingItWithoutPrintingTheToken(String chatId) {
        assertThatThrownBy(() -> AlertSettings.from(alerts(List.of(JAVA)), telegram(TOKEN, chatId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_CHAT_ID")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsAnEmptySearchList(List<AlertsProperties.Search> searches) {
        assertThatThrownBy(() -> AlertSettings.from(alerts(searches), telegram(TOKEN, "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alerts.searches");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "           | Brazil | false | 25  | alerts.searches[1].keywords",
            "java       | Brazil | maybe | 25  | alerts.searches[1].remote",
            "java       | Brazil | false | 0   | alerts.searches[1].maxResults",
            "java       | Brazil | false | 101 | alerts.searches[1].maxResults",
    })
    void rejectsAnInvalidSearchNamingItsIndexAndField(
            String keywords, String location, String remote, int maxResults, String expectedSetting) {
        AlertsProperties.Search invalid =
                new AlertsProperties.Search(keywords, location, PostedWithin.ANY, remote, maxResults);

        assertThatThrownBy(() -> AlertSettings.from(alerts(List.of(JAVA, invalid)), telegram(TOKEN, "1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedSetting);
    }

    @Test
    void rejectsATooLongLocationNamingIt() {
        AlertsProperties.Search invalid =
                new AlertsProperties.Search("java", "a".repeat(101), PostedWithin.ANY, "false", 25);

        assertThatThrownBy(() -> AlertSettings.from(alerts(List.of(invalid)), telegram(TOKEN, "1")))
                .hasMessageContaining("alerts.searches[0].location");
    }

    @Test
    void bindsDocumentedDefaultsWhenNothingIsConfigured() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "alerts.searches[0].keywords", "java",
                "telegram.bot-token", TOKEN)));

        AlertsProperties alerts = binder.bindOrCreate("alerts", AlertsProperties.class);
        TelegramProperties telegram = binder.bindOrCreate("telegram", TelegramProperties.class);

        assertThat(alerts.enabled()).isTrue();
        assertThat(alerts.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(alerts.dbPath()).isEqualTo("./data/jobs.db");
        assertThat(alerts.searches()).containsExactly(
                new AlertsProperties.Search("java", null, PostedWithin.ANY, "false", 25));
        assertThat(telegram.baseUrl()).isEqualTo("https://api.telegram.org");
        assertThat(telegram.timeout()).isEqualTo(Duration.ofSeconds(10));
    }

    private static AlertsProperties alerts(List<AlertsProperties.Search> searches) {
        return new AlertsProperties(true, Duration.ofHours(1), "./data/jobs.db", searches);
    }

    private static TelegramProperties telegram(String token, String chatId) {
        return new TelegramProperties("https://api.telegram.org", token, chatId, Duration.ofSeconds(10));
    }
}
