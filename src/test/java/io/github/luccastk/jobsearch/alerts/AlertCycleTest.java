package io.github.luccastk.jobsearch.alerts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.github.luccastk.jobsearch.JobSearchService;
import io.github.luccastk.jobsearch.PostedWithin;
import io.github.luccastk.jobsearch.SearchInterruptedException;
import io.github.luccastk.jobsearch.SearchQuery;
import io.github.luccastk.jobsearch.linkedin.JobCardParser;
import io.github.luccastk.jobsearch.linkedin.LinkedInGuestClient;
import io.github.luccastk.jobsearch.linkedin.LinkedInProperties;
import io.github.luccastk.jobsearch.telegram.TelegramClient;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class AlertCycleTest {

    private static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String TOKEN = "123456:cycle-test-token";
    private static final String SEND_PATH = "/bot" + TOKEN + "/sendMessage";
    private static final long PAGE_DELAY_MS = 200;
    private static final Duration FAST_SEND_INTERVAL = Duration.ofMillis(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension telegram = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tempDir;

    private JobSearchService service;
    private SeenJobStore store;
    private TelegramClient telegramClient;

    @BeforeEach
    void setUp() {
        LinkedInProperties linkedInProperties = new LinkedInProperties(
                linkedIn.baseUrl(), "TestBrowser/1.0", Duration.ofMillis(PAGE_DELAY_MS), Duration.ofMillis(500));
        service = new JobSearchService(new LinkedInGuestClient(RestClient.builder(), linkedInProperties),
                new JobCardParser(), linkedInProperties);
        store = new SeenJobStore(tempDir.resolve("jobs.db").toString());
        telegramClient = new TelegramClient(RestClient.builder(),
                new TelegramProperties(telegram.baseUrl(), TOKEN, "987654", Duration.ofMillis(500)));
        telegram.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"ok\": true, \"result\": {}}")));
    }

    // --- First run (seeding)

    @Test
    void seedsEveryJobFoundWithoutSendingWhenTheStoreIsEmpty(CapturedOutput output) {
        stubSearch("java", cards(1, 2));
        stubSearch("kotlin", cards(3, 1));

        cycle(search("java"), search("kotlin")).run();

        assertThat(Stream.of("1", "2", "3")).allMatch(store::contains);
        telegram.verify(0, anyRequestedFor(anyUrl()));
        assertThat(infoLines(output)).anyMatch(line -> line.contains("Seeded 3 jobs"));
    }

    @Test
    void seedsAgainOnTheNextCycleWhenEverySearchFailedWhileSeeding() {
        linkedIn.stubFor(get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(503)));
        AlertCycle cycle = cycle(search("java"));

        cycle.run();
        assertThat(store.isEmpty()).isTrue();

        linkedIn.resetAll();
        stubSearch("java", cards(1, 2));
        cycle.run();

        assertThat(store.contains("1")).isTrue();
        assertThat(store.contains("2")).isTrue();
        telegram.verify(0, anyRequestedFor(anyUrl()));
    }

    // --- Sending

    @Test
    void sendsOneHtmlMessagePerNewJobInTheOrderFoundAndMarksThemSeen() {
        store.markSeen("1");
        stubSearch("java", cards(1, 2));
        stubSearch("kotlin", cards(3, 1));

        cycle(search("java"), search("kotlin")).run();

        assertThat(sentTexts()).containsExactly(
                "<b>Job 2</b>\nhttps://www.linkedin.com/jobs/view/2",
                "<b>Job 3</b>\nhttps://www.linkedin.com/jobs/view/3");
        assertThat(store.contains("2")).isTrue();
        assertThat(store.contains("3")).isTrue();
    }

    @Test
    void sendsAJobMatchedByTwoSearchesOnlyOnce() {
        store.markSeen("0");
        stubSearch("java", cards(5, 1));
        stubSearch("kotlin", cards(5, 1));

        cycle(search("java"), search("kotlin")).run();

        assertThat(sentTexts()).containsExactly("<b>Job 5</b>\nhttps://www.linkedin.com/jobs/view/5");
    }

    @Test
    void sendsNothingForJobsAlreadySeen() {
        store.markSeen("1");
        store.markSeen("2");
        stubSearch("java", cards(1, 2));

        cycle(search("java")).run();

        telegram.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void waitsAtLeastOneSecondBetweenMessages() {
        store.markSeen("0");
        stubSearch("java", cards(1, 3));

        new AlertCycle(List.of(search("java")), service, store, telegramClient, Duration.ofMillis(PAGE_DELAY_MS))
                .run();

        List<Long> times = sentInOrder().stream().map(e -> e.getRequest().getLoggedDate().getTime()).toList();
        assertThat(times).hasSize(3);
        assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(1000);
        assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void marksEachJobSeenRightAfterItsMessageIsSent() {
        store.markSeen("0");
        stubSearch("java", cards(1, 2));
        SeenJobStore storeSpy = spy(store);
        TelegramClient telegramSpy = spy(telegramClient);

        new AlertCycle(List.of(search("java")), service, storeSpy, telegramSpy, Duration.ofMillis(PAGE_DELAY_MS),
                FAST_SEND_INTERVAL).run();

        InOrder inOrder = inOrder(storeSpy, telegramSpy);
        inOrder.verify(telegramSpy).sendMessage(contains("Job 1"));
        inOrder.verify(storeSpy).markSeen("1");
        inOrder.verify(telegramSpy).sendMessage(contains("Job 2"));
        inOrder.verify(storeSpy).markSeen("2");
    }

    @ParameterizedTest
    @MethodSource("sendFailures")
    void stopsSendingAtTheFirstFailedMessageAndLeavesTheRestUnseen(
            ResponseDefinitionBuilder failure, String expectedReason, CapturedOutput output) {
        store.markSeen("0");
        stubSearch("java", cards(1, 3));
        telegram.stubFor(post(urlPathEqualTo(SEND_PATH)).withRequestBody(containing("Job 2")).willReturn(failure));

        cycle(search("java")).run();

        assertThat(sentInOrder()).hasSize(2);
        assertThat(store.contains("1")).isTrue();
        assertThat(store.contains("2")).isFalse();
        assertThat(store.contains("3")).isFalse();
        assertThat(warnLines(output)).singleElement().asString().contains("job 2").contains(expectedReason);
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    static Stream<Arguments> sendFailures() {
        return Stream.of(
                Arguments.of(aResponse().withStatus(500), "HTTP 500"),
                Arguments.of(okJson("{\"ok\": false}"), "ok=false"),
                Arguments.of(okJson("{\"ok\": true}").withFixedDelay(1500), "TimeoutException"),
                Arguments.of(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER), "Exception"));
    }

    @Test
    void retriesUnsentJobsOnTheNextCycle() {
        store.markSeen("0");
        stubSearch("java", cards(1, 1));
        telegram.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(500)));
        AlertCycle cycle = cycle(search("java"));
        cycle.run();

        telegram.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"ok\": true}")));
        cycle.run();

        assertThat(sentInOrder()).hasSize(2);
        assertThat(store.contains("1")).isTrue();
    }

    // --- Searches

    @Test
    void waitsThePageDelayBetweenSearches() {
        stubSearch("java", "");
        stubSearch("kotlin", "");

        cycle(search("java"), search("kotlin")).run();

        List<ServeEvent> requests = linkedIn.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .toList();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).getRequest().getLoggedDate().getTime()
                - requests.get(0).getRequest().getLoggedDate().getTime()).isGreaterThanOrEqualTo(PAGE_DELAY_MS);
    }

    @Test
    void logsAFailedSearchAndContinuesWithTheNext(CapturedOutput output) {
        store.markSeen("0");
        linkedIn.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withQueryParam("keywords", equalTo("java"))
                .willReturn(aResponse().withStatus(503)));
        stubSearch("kotlin", cards(7, 1));

        cycle(search("java"), search("kotlin")).run();

        assertThat(sentTexts()).containsExactly("<b>Job 7</b>\nhttps://www.linkedin.com/jobs/view/7");
        assertThat(alertWarnLines(output)).singleElement().asString().contains("java").contains("503");
    }

    @ParameterizedTest
    @MethodSource("searchExceptions")
    void logsAnySearchExceptionAndContinuesWithTheNext(RuntimeException failure, CapturedOutput output) {
        store.markSeen("0");
        stubSearch("kotlin", cards(7, 1));
        JobSearchService serviceSpy = spy(service);
        doThrow(failure).when(serviceSpy).search(argThat(query -> query.keywords().equals("java")));

        new AlertCycle(List.of(search("java"), search("kotlin")), serviceSpy, store, telegramClient,
                Duration.ofMillis(PAGE_DELAY_MS), FAST_SEND_INTERVAL).run();

        assertThat(sentTexts()).hasSize(1);
        assertThat(alertWarnLines(output)).singleElement().asString().contains("java").contains(failure.getMessage());
    }

    static Stream<RuntimeException> searchExceptions() {
        return Stream.of(
                new SearchInterruptedException("search interrupted", null),
                new IllegalStateException("unexpected parser state"));
    }

    @Test
    void logsOneSummaryLinePerCycle(CapturedOutput output) {
        store.markSeen("1");
        stubSearch("java", cards(1, 2));
        linkedIn.stubFor(get(urlPathEqualTo(SEARCH_PATH)).withQueryParam("keywords", equalTo("go"))
                .willReturn(aResponse().withStatus(503)));
        stubSearch("kotlin", cards(2, 2));

        cycle(search("java"), search("go"), search("kotlin")).run();

        assertThat(infoLines(output).filter(line -> line.contains("Alert cycle finished")).toList())
                .singleElement().asString()
                .contains("searchesRun=3")
                .contains("searchesFailed=1")
                .contains("jobsFound=3")
                .contains("newJobs=2")
                .contains("messagesSent=2");
    }

    @Test
    void logsTheSummaryLineWhenInterruptedBetweenSearches(CapturedOutput output) {
        store.markSeen("0");
        stubSearch("java", cards(1, 1));
        stubSearch("kotlin", cards(2, 1));
        JobSearchService serviceSpy = spy(service);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            Thread.currentThread().interrupt();
            return result;
        }).when(serviceSpy).search(argThat(query -> query.keywords().equals("java")));

        new AlertCycle(List.of(search("java"), search("kotlin")), serviceSpy, store, telegramClient,
                Duration.ofMillis(PAGE_DELAY_MS), FAST_SEND_INTERVAL).run();

        assertThat(Thread.interrupted()).isTrue();
        telegram.verify(0, anyRequestedFor(anyUrl()));
        assertThat(infoLines(output).filter(line -> line.contains("Alert cycle finished")).toList())
                .singleElement().asString()
                .contains("searchesRun=1")
                .contains("messagesSent=0");
    }

    // --- Helpers

    private AlertCycle cycle(SearchQuery... searches) {
        return new AlertCycle(List.of(searches), service, store, telegramClient, Duration.ofMillis(PAGE_DELAY_MS),
                FAST_SEND_INTERVAL);
    }

    private static SearchQuery search(String keywords) {
        return new SearchQuery(keywords, null, PostedWithin.ANY, false, 25);
    }

    /** First page for {@code keywords} returns {@code html}; any later page is empty. */
    private static void stubSearch(String keywords, String html) {
        linkedIn.stubFor(get(urlPathEqualTo(SEARCH_PATH)).atPriority(2)
                .withQueryParam("keywords", equalTo(keywords))
                .willReturn(aResponse().withStatus(200).withBody("")));
        linkedIn.stubFor(get(urlPathEqualTo(SEARCH_PATH)).atPriority(1)
                .withQueryParam("keywords", equalTo(keywords))
                .withQueryParam("start", equalTo("0"))
                .willReturn(aResponse().withStatus(200).withBody(html)));
    }

    /** Minimal guest-endpoint cards with ids {@code firstId .. firstId + count - 1}. */
    private static String cards(int firstId, int count) {
        StringBuilder html = new StringBuilder();
        for (int id = firstId; id < firstId + count; id++) {
            html.append("""
                    <li><div class="base-card" data-entity-urn="urn:li:jobPosting:%d">
                      <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/%d?trk=x"></a>
                      <h3 class="base-search-card__title">Job %d</h3>
                    </div></li>
                    """.formatted(id, id, id));
        }
        return html.toString();
    }

    private static List<ServeEvent> sentInOrder() {
        return telegram.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .toList();
    }

    /** The {@code text} of each sendMessage request, in the order Telegram received them. */
    private static List<String> sentTexts() {
        return sentInOrder().stream().map(AlertCycleTest::text).toList();
    }

    private static String text(ServeEvent event) {
        try {
            return JSON.readTree(event.getRequest().getBodyAsString()).get("text").asText();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Stream<String> infoLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("INFO"));
    }

    private static List<String> warnLines(CapturedOutput output) {
        return output.getOut().lines()
                .filter(line -> line.contains("WARN") && line.contains("Telegram"))
                .toList();
    }

    private static List<String> alertWarnLines(CapturedOutput output) {
        return output.getOut().lines()
                .filter(line -> line.contains("WARN") && line.contains("Alert search failed"))
                .toList();
    }
}
