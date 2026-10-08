package io.github.luccastk.jobsearch.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class CallbackPollerTest {

    private static final String TOKEN = "123456:poller-test-token";
    private static final String UPDATES_PATH = "/bot" + TOKEN + "/getUpdates";
    private static final String CHAT_ID = "987654";
    private static final Duration LONG_POLL = Duration.ofSeconds(1);

    @RegisterExtension
    static WireMockExtension telegram = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final List<CallbackQuery> handled = new CopyOnWriteArrayList<>();
    private TelegramClient client;
    private CallbackPoller poller;

    @BeforeEach
    void setUp() {
        client = new TelegramClient(RestClient.builder(),
                new TelegramProperties(telegram.baseUrl(), TOKEN, CHAT_ID, Duration.ofMillis(500)));
        // Every poll past the scripted ones is an empty long poll, held briefly like Telegram holds it.
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH)).atPriority(9)
                .willReturn(okJson("{\"ok\": true, \"result\": []}").withFixedDelay(100)));
    }

    @AfterEach
    void tearDown() {
        if (poller != null) {
            poller.stop();
        }
    }

    @Test
    void handsCallbacksFromTheConfiguredChatToTheHandlerOnce() {
        stubUpdatesAt(0, callback(41, "cb-1", CHAT_ID, "resume:4242"));

        start();

        await().atMost(Duration.ofSeconds(5)).until(() -> !handled.isEmpty());
        awaitPollAt(42);
        assertThat(handled).containsExactly(new CallbackQuery("cb-1", CHAT_ID, "resume:4242"));
    }

    @Test
    void ignoresCallbacksFromAnyOtherChatWithOneWarnLineWithoutTheirContent(CapturedOutput output) {
        stubUpdatesAt(0, callback(41, "cb-secret", "111222", "resume:4242"));

        start();

        awaitPollAt(42);
        assertThat(handled).isEmpty();
        assertThat(warnLines(output)).singleElement().asString()
                .doesNotContain("cb-secret").doesNotContain("111222").doesNotContain("resume:4242");
        assertThat(telegram.getAllServeEvents()).extracting(e -> e.getRequest().getUrl())
                .allMatch(url -> url.endsWith("/getUpdates"));
    }

    @Test
    void ignoresUpdatesThatAreNotCallbackQueries(CapturedOutput output) {
        stubUpdatesAt(0, """
                {"update_id": 41, "message": {"message_id": 1, "chat": {"id": 987654}, "text": "oi"}}""");

        start();

        awaitPollAt(42);
        assertThat(handled).isEmpty();
        assertThat(warnLines(output)).isEmpty();
    }

    @Test
    void keepsPollingWhenTheHandlerThrows() {
        stubUpdatesAt(0, callback(41, "cb-1", CHAT_ID, "resume:1"));
        stubUpdatesAt(42, callback(42, "cb-2", CHAT_ID, "resume:2"));
        poller = new CallbackPoller(client, CHAT_ID, query -> {
            handled.add(query);
            throw new IllegalStateException("boom");
        }, LONG_POLL);
        poller.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> handled.size() == 2);
    }

    @Test
    void keepsPollingWhenAPollFailsUnexpectedly() {
        TelegramClient failingOnce = new TelegramClient(RestClient.builder(),
                new TelegramProperties(telegram.baseUrl(), TOKEN, CHAT_ID, Duration.ofMillis(500))) {
            private boolean failed;

            @Override
            public List<TelegramUpdate> getUpdates(long offset, Duration longPoll) {
                if (!failed) {
                    failed = true;
                    throw new IllegalStateException("unexpected");
                }
                return super.getUpdates(offset, longPoll);
            }
        };
        stubUpdatesAt(0, callback(41, "cb-1", CHAT_ID, "resume:4242"));
        poller = new CallbackPoller(failingOnce, CHAT_ID, handled::add, LONG_POLL);
        poller.start();

        await().atMost(Duration.ofSeconds(10)).until(() -> !handled.isEmpty());
        assertThat(handled).containsExactly(new CallbackQuery("cb-1", CHAT_ID, "resume:4242"));
    }

    @Test
    void waitsAtLeastFiveSecondsAfterAFailedPollAndLogsWithoutTheToken(CapturedOutput output) {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH)).willReturn(aResponse().withStatus(502)));

        start();

        await().atMost(Duration.ofSeconds(10)).until(() -> polls().size() >= 2);
        List<Long> times = polls().stream().map(e -> e.getRequest().getLoggedDate().getTime()).toList();
        assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(5000);
        assertThat(warnLines(output)).isNotEmpty().allMatch(line -> line.contains("HTTP 502"));
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void stopsPromptlyWithoutAStackTraceWhileAPollIsInFlight(CapturedOutput output) {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH))
                .willReturn(okJson("{\"ok\": true, \"result\": []}").withFixedDelay(5000)));
        start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !polls().isEmpty());

        long started = System.nanoTime();
        poller.stop();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        assertThat(poller.isRunning()).isFalse();
        assertThat(output.getAll()).doesNotContain("Exception").doesNotContain("\tat ");
    }

    private void start() {
        poller = new CallbackPoller(client, CHAT_ID, handled::add, LONG_POLL);
        poller.start();
    }

    private static void stubUpdatesAt(long offset, String update) {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH)).atPriority(1)
                .withRequestBody(matchingJsonPath("$.offset", equalTo(String.valueOf(offset))))
                .willReturn(okJson("{\"ok\": true, \"result\": [" + update + "]}")));
    }

    private static String callback(long updateId, String id, String chatId, String data) {
        return """
                {"update_id": %d, "callback_query": {"id": "%s", "from": {"id": 1}, "data": "%s",
                  "message": {"message_id": 9, "chat": {"id": %s}}}}""".formatted(updateId, id, data, chatId);
    }

    private static void awaitPollAt(long offset) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> telegram.verify(postRequestedFor(
                urlPathEqualTo(UPDATES_PATH)).withRequestBody(
                        matchingJsonPath("$.offset", equalTo(String.valueOf(offset))))));
    }

    private static List<ServeEvent> polls() {
        return telegram.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .toList();
    }

    private static List<String> warnLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("WARN")).toList();
    }
}
