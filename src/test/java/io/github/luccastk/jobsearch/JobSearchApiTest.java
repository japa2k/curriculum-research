package io.github.luccastk.jobsearch;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.github.luccastk.jobsearch.linkedin.LinkedInGuestClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class JobSearchApiTest {

    private static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TestBrowser/1.0";
    private static final long PAGE_DELAY_MS = 200;

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void linkedInProperties(DynamicPropertyRegistry registry) {
        registry.add("linkedin.base-url", linkedIn::baseUrl);
        registry.add("linkedin.user-agent", () -> USER_AGENT);
        registry.add("linkedin.page-delay", () -> PAGE_DELAY_MS + "ms");
        registry.add("linkedin.timeout", () -> "500ms");
        // REST-only: no scheduled cycles hitting the LinkedIn stub, no Telegram settings needed.
        registry.add("alerts.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private LinkedInGuestClient client;

    // --- Endpoint and parameters

    @Test
    void returnsParsedJobsAsJson() throws Exception {
        stubPage(0, fixture("search-page-java-brazil.html"));
        stubPage(10, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.partial").value(false))
                .andExpect(jsonPath("$.jobs.length()").value(10))
                .andExpect(jsonPath("$.jobs[0].id").value("4459064563"))
                .andExpect(jsonPath("$.jobs[0].title")
                        .value("Java & Kotlin Developer – Spring Framework - Remote Work | REF#294652"))
                .andExpect(jsonPath("$.jobs[0].company").value("BairesDev"))
                .andExpect(jsonPath("$.jobs[0].location").value("São Paulo, São Paulo, Brazil"))
                .andExpect(jsonPath("$.jobs[0].url").value(
                        "https://br.linkedin.com/jobs/view/java-kotlin-developer-%E2%80%93-spring-framework-remote-work-ref%23294652-at-bairesdev-4459064563"))
                .andExpect(jsonPath("$.jobs[0].postedAt").value("2026-09-08"));
    }

    @Test
    void writesMissingFieldsAsExplicitNulls() throws Exception {
        stubPage(0, fixture("search-page-edge-cases.html"));
        stubPage(4, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"count": 2, "partial": false, "jobs": [
                          {"id": "1001", "title": "No company or location", "company": null, "location": null,
                           "url": "https://www.linkedin.com/jobs/view/no-company-1001", "postedAt": null},
                          {"id": "1003", "title": "No date", "company": "Acme", "location": "Remote",
                           "url": "https://www.linkedin.com/jobs/view/no-date-1003", "postedAt": null}
                        ]}""", true));
    }

    @ParameterizedTest
    @CsvSource({"DAY, r86400", "WEEK, r604800", "MONTH, r2592000"})
    void sendsSearchFiltersToLinkedIn(String postedWithin, String expectedTpr) throws Exception {
        stubPage(0, "");

        mockMvc.perform(get("/api/jobs/search")
                        .param("keywords", "  java developer  ")
                        .param("location", "Brazil")
                        .param("postedWithin", postedWithin)
                        .param("remote", "true"))
                .andExpect(status().isOk());

        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("keywords", equalTo("java developer"))
                .withQueryParam("location", equalTo("Brazil"))
                .withQueryParam("f_TPR", equalTo(expectedTpr))
                .withQueryParam("f_WT", equalTo("2"))
                .withQueryParam("start", equalTo("0")));
    }

    @Test
    void omitsOptionalFiltersByDefault() throws Exception {
        stubPage(0, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java").param("postedWithin", "ANY"))
                .andExpect(status().isOk());

        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("keywords", equalTo("java"))
                .withQueryParam("location", absent())
                .withQueryParam("f_TPR", absent())
                .withQueryParam("f_WT", absent()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "                 |                  | keywords",
            "keywords=        |                  | keywords",
            "keywords=java    | postedWithin=YEAR | postedWithin",
            "keywords=java    | remote=maybe     | remote",
            "keywords=java    | remote=1         | remote",
            "keywords=java    | remote=yes       | remote",
            "keywords=java    | maxResults=0     | maxResults",
            "keywords=java    | maxResults=101   | maxResults",
            "keywords=java    | maxResults=abc   | maxResults",
    })
    void rejectsInvalidParametersWithoutCallingLinkedIn(String first, String second, String invalidParam)
            throws Exception {
        String query = String.join("&", nullToEmpty(first), nullToEmpty(second));

        mockMvc.perform(get("/api/jobs/search?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString(invalidParam)));

        linkedIn.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("blankOrTooLongKeywords")
    void rejectsBlankOrTooLongKeywords(String keywords) throws Exception {
        mockMvc.perform(get("/api/jobs/search").param("keywords", keywords))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("keywords")));

        linkedIn.verify(0, anyRequestedFor(anyUrl()));
    }

    static Stream<String> blankOrTooLongKeywords() {
        return Stream.of("   ", "a".repeat(101));
    }

    @Test
    void acceptsKeywordsOfExactlyTheMaximumLengthAfterTrim() throws Exception {
        stubPage(0, "");
        String keywords = "a".repeat(100);

        mockMvc.perform(get("/api/jobs/search").param("keywords", "  " + keywords + "  "))
                .andExpect(status().isOk());

        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("keywords", equalTo(keywords)));
    }

    @Test
    void rejectsTooLongLocationWithoutCallingLinkedIn() throws Exception {
        mockMvc.perform(get("/api/jobs/search").param("keywords", "java").param("location", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("location")));

        linkedIn.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void doesNotEchoTheRejectedValueInTheError() throws Exception {
        mockMvc.perform(get("/api/jobs/search").param("keywords", "java").param("postedWithin", "<script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("postedWithin")))
                .andExpect(jsonPath("$.error").value(not(containsString("<script>"))));
    }

    @Test
    void appliesDefaultsWhenOptionalParametersAreOmitted() throws Exception {
        stubPage(0, cards(1, 10));
        stubPage(10, cards(11, 10));
        stubPage(20, cards(21, 10));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(25))
                .andExpect(jsonPath("$.jobs[*].id").value(contains(ids(1, 25))));

        linkedIn.verify(3, getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("location", absent())
                .withQueryParam("f_TPR", absent())
                .withQueryParam("f_WT", absent()));
    }

    // --- Fetching and pagination

    @Test
    void advancesStartByTheNumberOfCardsOnThePreviousPage() throws Exception {
        stubPage(0, cards(1, 6) + cardWithoutId());
        stubPage(7, cards(7, 7));
        stubPage(14, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(13));

        assertThat(requestedStarts()).containsExactly("0", "7", "14");
    }

    @Test
    void stopsOnceMaxResultsUniqueJobsAreCollected() throws Exception {
        stubPage(0, cards(1, 10));
        stubPage(10, cards(6, 10));
        stubPage(20, cards(16, 10));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java").param("maxResults", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(12))
                .andExpect(jsonPath("$.jobs[*].id").value(contains(ids(1, 12))));

        assertThat(requestedStarts()).containsExactly("0", "10");
    }

    @Test
    void requestsAtMostTenPages() throws Exception {
        for (int start = 0; start <= 10; start++) {
            stubPage(start, cards(start + 1, 1));
        }

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java").param("maxResults", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.partial").value(false));

        assertThat(requestedStarts()).hasSize(10);
    }

    @Test
    void waitsTheConfiguredDelayBetweenPages() throws Exception {
        stubPage(0, cards(1, 10));
        stubPage(10, cards(11, 10));
        stubPage(20, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java")).andExpect(status().isOk());

        List<Long> times = servedInOrder().stream().map(e -> e.getRequest().getLoggedDate().getTime()).toList();
        assertThat(times).hasSize(3);
        assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(PAGE_DELAY_MS);
        assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(PAGE_DELAY_MS);
    }

    @Test
    void sendsABrowserUserAgentAndNoCredentials() throws Exception {
        stubPage(0, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java")).andExpect(status().isOk());

        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                .withHeader("User-Agent", equalTo(USER_AGENT))
                .withoutHeader("Cookie")
                .withoutHeader("Authorization"));
    }

    @Test
    void returnsAnEmptyResultWhenTheFirstPageHasNoCards() throws Exception {
        stubPage(0, "");

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"count": 0, "partial": false, "jobs": []}""", true));
    }

    // --- Upstream failures

    @ParameterizedTest
    @CsvSource({"302", "429", "503", "999"})
    void answers502WhenTheFirstPageFails(int upstreamStatus, CapturedOutput output) throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(upstreamStatus)));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString(String.valueOf(upstreamStatus))));

        assertThat(warnLines(output)).singleElement().asString()
                .contains("start=0").contains(String.valueOf(upstreamStatus));
    }

    @Test
    void answers502WhenTheFirstPageTimesOut(CapturedOutput output) throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withStatus(200).withBody("").withFixedDelay(1500)));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("timeout")));

        assertThat(warnLines(output)).singleElement().asString().contains("start=0").contains("Timeout");
    }

    @Test
    void answers502WhenTheConnectionFails(CapturedOutput output) throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").isNotEmpty());

        assertThat(warnLines(output)).singleElement().asString().contains("start=0").contains("Exception");
    }

    @Test
    void returnsPartialResultsWhenALaterPageFails(CapturedOutput output) throws Exception {
        stubPage(0, cards(1, 10));
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).withQueryParam("start", equalTo("10"))
                .willReturn(aResponse().withStatus(429)));

        mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.jobs[*].id").value(contains(ids(1, 10))));

        assertThat(requestedStarts()).containsExactly("0", "10");
        assertThat(warnLines(output)).singleElement().asString().contains("start=10").contains("429");
    }

    @Test
    void answersAJsonErrorWhenInterruptedBetweenPages() throws Exception {
        stubPage(0, cards(1, 10));
        doAnswer(invocation -> {
            Object page = invocation.callRealMethod();
            Thread.currentThread().interrupt();
            return page;
        }).when(client).fetchPage(any(), eq(0));

        try {
            mockMvc.perform(get("/api/jobs/search").param("keywords", "java"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value(containsString("interrupted")));
        } finally {
            Thread.interrupted();
        }

        assertThat(requestedStarts()).containsExactly("0");
    }

    // --- Helpers

    private static void stubPage(int start, String html) {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("start", equalTo(String.valueOf(start)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(html)));
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

    private static String cardWithoutId() {
        return """
                <li><div class="base-card"><h3 class="base-search-card__title">No id</h3></div></li>
                """;
    }

    private static String[] ids(int first, int last) {
        return IntStream.rangeClosed(first, last).mapToObj(String::valueOf).toArray(String[]::new);
    }

    private static List<ServeEvent> servedInOrder() {
        return linkedIn.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .toList();
    }

    private static List<String> requestedStarts() {
        return servedInOrder().stream().map(e -> e.getRequest().queryParameter("start").firstValue()).toList();
    }

    private static List<String> warnLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("LinkedIn")).toList();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = JobSearchApiTest.class.getResourceAsStream("/linkedin/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
