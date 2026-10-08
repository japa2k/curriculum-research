package io.github.luccastk.jobsearch.recommendation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The test profile runs 2 searches ("java developer" CORE, "kotlin" BACKEND_JVM) × 2 locations. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RecommendationsApiTest {

    private static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String DETAIL_PATH = "/jobs-guest/jobs/api/jobPosting/";
    private static final long PAGE_DELAY_MS = 100;
    private static final String BRAZIL = "Brazil";
    private static final String SAO_PAULO = "São Paulo, Brazil";

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("linkedin.base-url", linkedIn::baseUrl);
        registry.add("linkedin.page-delay", () -> PAGE_DELAY_MS + "ms");
        registry.add("linkedin.timeout", () -> "500ms");
        registry.add("profile.location", () -> "classpath:profiles/recommendations.yml");
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void searchesReturnNothingUnlessStubbed() {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).atPriority(10)
                .willReturn(html("")));
        linkedIn.stubFor(WireMock.get(urlPathMatching(DETAIL_PATH + ".*")).atPriority(10)
                .willReturn(aResponse().withStatus(404)));
    }

    // --- Parameters and searches

    @Test
    void runsOneFirstPageSearchPerSearchLocationPairWithDefaults() throws Exception {
        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"count": 0, "partial": false, "jobs": []}""", true));

        assertThat(searchesInOrder()).containsExactly(
                "java developer|Brazil|r604800|2|0",
                "java developer|São Paulo, Brazil|r604800|-|0",
                "kotlin|Brazil|r604800|2|0",
                "kotlin|São Paulo, Brazil|r604800|-|0");
        linkedIn.verify(0, getRequestedFor(urlPathMatching(DETAIL_PATH + ".*")));
    }

    @ParameterizedTest
    @CsvSource({"DAY, r86400", "MONTH, r2592000"})
    void sendsTheRequestedPostedWithin(String postedWithin, String expectedTpr) throws Exception {
        mockMvc.perform(get("/api/jobs/recommendations").param("postedWithin", postedWithin))
                .andExpect(status().isOk());

        linkedIn.verify(4, getRequestedFor(urlPathEqualTo(SEARCH_PATH)).withQueryParam("f_TPR", equalTo(expectedTpr)));
    }

    @Test
    void omitsTheDateFilterForAny() throws Exception {
        mockMvc.perform(get("/api/jobs/recommendations").param("postedWithin", "ANY"))
                .andExpect(status().isOk());

        assertThat(searchesInOrder()).hasSize(4).allMatch(search -> search.split("\\|")[2].equals("-"));
    }

    @ParameterizedTest
    @CsvSource({"postedWithin, YEAR", "maxResults, 0", "maxResults, 51", "maxResults, abc"})
    void rejectsInvalidParametersWithoutCallingLinkedIn(String name, String value) throws Exception {
        mockMvc.perform(get("/api/jobs/recommendations").param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString(name)));

        linkedIn.verify(0, anyRequestedFor(anyUrl()));
    }

    // --- Scoring and classification

    @Test
    void classifiesAndRanksPostingsAgainstTheProfile() throws Exception {
        stubSearch("java developer", BRAZIL,
                card("101", "Desenvolvedor Java Pleno", "2026-10-01")
                        + card("102", "Backend Developer", "2026-10-02")
                        + card("103", "Platform Engineer", "2026-10-03")
                        + card("105", "Dev Jr", "2026-09-30"));
        stubDetail("101", detail("Desenvolvedor Java Pleno", "Java and SQL. Node is a plus.", null));
        stubDetail("102", detail("Backend Developer", "Java, Kotlin and AWS.", "Entry level"));
        stubDetail("103", detail("Platform Engineer", "AWS, Kubernetes, Terraform, GraphQL; some Java.",
                "Mid-Senior level"));
        stubDetail("105", detail("Dev Jr", "Kotlin services.", "Associate"));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"count": 4, "partial": false, "jobs": [
                          {"id": "101", "title": "Desenvolvedor Java Pleno", "company": "Acme",
                           "location": "Brazil", "url": "https://www.linkedin.com/jobs/view/101",
                           "postedAt": "2026-10-01", "track": "CORE", "seniority": "PLENO",
                           "descriptionAvailable": true, "matchedSkills": ["Java", "Node.js", "SQL"],
                           "missingSkills": [], "category": "MATCH"},
                          {"id": "105", "title": "Dev Jr", "company": "Acme",
                           "location": "Brazil", "url": "https://www.linkedin.com/jobs/view/105",
                           "postedAt": "2026-09-30", "track": "CORE", "seniority": "JUNIOR",
                           "descriptionAvailable": true, "matchedSkills": [],
                           "missingSkills": ["Kotlin"], "category": "STUDYABLE"},
                          {"id": "102", "title": "Backend Developer", "company": "Acme",
                           "location": "Brazil", "url": "https://www.linkedin.com/jobs/view/102",
                           "postedAt": "2026-10-02", "track": "CORE", "seniority": "JUNIOR",
                           "descriptionAvailable": true, "matchedSkills": ["Java"],
                           "missingSkills": ["AWS", "Kotlin"], "category": "STUDYABLE"},
                          {"id": "103", "title": "Platform Engineer", "company": "Acme",
                           "location": "Brazil", "url": "https://www.linkedin.com/jobs/view/103",
                           "postedAt": "2026-10-03", "track": "CORE", "seniority": "UNKNOWN",
                           "descriptionAvailable": true, "matchedSkills": ["Java"],
                           "missingSkills": ["AWS", "GraphQL", "Kubernetes", "Terraform"], "category": "STRETCH"}
                        ]}""", true));
    }

    @Test
    void detectsSkillsInTheTitleToo() throws Exception {
        stubSearch("kotlin", BRAZIL, card("201", "Kotlin Developer", null));
        stubDetail("201", detail("Kotlin Developer", "Join our team.", null));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].missingSkills").value(contains("Kotlin")))
                .andExpect(jsonPath("$.jobs[0].category").value("STUDYABLE"));
    }

    @Test
    void ordersByCategoryThenMissingCountThenNewestThenId() throws Exception {
        stubSearch("java developer", BRAZIL,
                card("30", "A", "2026-10-01") + card("4", "B", "2026-10-01") + card("7", "C", null)
                        + card("8", "D", "2026-10-05") + card("9", "E", "2026-10-09") + card("10", "F", "2026-10-09"));
        stubDetail("30", detail("A", "Kotlin", null));
        stubDetail("4", detail("B", "Kotlin", null));
        stubDetail("7", detail("C", "Kotlin", null));
        stubDetail("8", detail("D", "Kotlin", null));
        stubDetail("9", detail("E", "Kotlin, AWS", null));
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "10")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[*].id").value(contains("8", "4", "30", "7", "9", "10")))
                .andExpect(jsonPath("$.jobs[*].category").value(
                        contains("STUDYABLE", "STUDYABLE", "STUDYABLE", "STUDYABLE", "STUDYABLE", "UNRATED")));
    }

    // --- Merging, filtering, limits and pacing

    @Test
    void mergesDuplicatesKeepingTheFirstTrackInProfileOrder() throws Exception {
        stubSearch("java developer", SAO_PAULO, card("301", "Kotlin + Java Dev", null));
        stubSearch("kotlin", BRAZIL, card("301", "Kotlin + Java Dev", null) + card("302", "Kotlin Dev", null));
        stubDetail("301", detail("Kotlin + Java Dev", "Kotlin", null));
        stubDetail("302", detail("Kotlin Dev", "Kotlin", null));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.jobs[*].id").value(contains("301", "302")))
                .andExpect(jsonPath("$.jobs[*].track").value(contains("CORE", "BACKEND_JVM")));

        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(DETAIL_PATH + "301")));
    }

    @Test
    void dropsSeniorTitlesWithoutFetchingTheirDetails() throws Exception {
        stubSearch("java developer", BRAZIL,
                card("401", "Senior Java Developer", null) + card("402", "Tech Lead Java", null)
                        + card("403", "Java Developer", null));
        stubDetail("403", detail("Java Developer", "Java", null));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[*].id").value(contains("403")));

        assertThat(detailsInOrder()).containsExactly("403");
    }

    @Test
    void returnsAnEmptyResultWithoutDetailsWhenEverySearchResultIsSenior() throws Exception {
        stubSearch("java developer", BRAZIL, card("401", "Senior Java Developer", null));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"count": 0, "partial": false, "jobs": []}""", true));

        assertThat(detailsInOrder()).isEmpty();
    }

    @Test
    void fetchesAtMostThirtyDetailsByDefault() throws Exception {
        stubSearch("java developer", BRAZIL, cards(1001, 10));
        stubSearch("java developer", SAO_PAULO, cards(1011, 10));
        stubSearch("kotlin", BRAZIL, cards(1021, 10));
        stubSearch("kotlin", SAO_PAULO, cards(1031, 10));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(30));

        assertThat(detailsInOrder()).hasSize(30);
    }

    @Test
    void acceptsMaxResultsOfFifty() throws Exception {
        mockMvc.perform(get("/api/jobs/recommendations").param("maxResults", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void selectsPostingsRoundRobinAcrossTracks() throws Exception {
        stubSearch("java developer", BRAZIL, card("501", "Dev 1", null) + card("502", "Dev 2", null));
        stubSearch("java developer", SAO_PAULO, card("503", "Dev 3", null));
        stubSearch("kotlin", BRAZIL, card("504", "Dev 4", null) + card("505", "Dev 5", null));
        for (String id : List.of("501", "502", "503", "504", "505")) {
            stubDetail(id, detail("Dev", "Java", null));
        }

        mockMvc.perform(get("/api/jobs/recommendations").param("maxResults", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));

        assertThat(detailsInOrder()).containsExactly("501", "504", "502", "505");
    }

    @Test
    void givesEveryTrackAPostingBeforeAnyTrackGetsASecond() throws Exception {
        stubSearch("java developer", BRAZIL, card("501", "Dev 1", null) + card("502", "Dev 2", null));
        stubSearch("kotlin", SAO_PAULO, card("504", "Dev 4", null));
        for (String id : List.of("501", "502", "504")) {
            stubDetail(id, detail("Dev", "Java", null));
        }

        mockMvc.perform(get("/api/jobs/recommendations").param("maxResults", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[*].track").value(contains("CORE", "BACKEND_JVM")));

        assertThat(detailsInOrder()).containsExactly("501", "504");
    }

    @Test
    void waitsTheConfiguredDelayBetweenEveryUpstreamRequest() throws Exception {
        stubSearch("java developer", BRAZIL, card("601", "Dev", null) + card("602", "Dev", null));
        stubDetail("601", detail("Dev", "Java", null));
        stubDetail("602", detail("Dev", "Java", null));

        mockMvc.perform(get("/api/jobs/recommendations")).andExpect(status().isOk());

        List<Long> times = servedInOrder().stream().map(e -> e.getRequest().getLoggedDate().getTime()).toList();
        assertThat(times).hasSize(6);
        for (int i = 1; i < times.size(); i++) {
            assertThat(times.get(i) - times.get(i - 1)).isGreaterThanOrEqualTo(PAGE_DELAY_MS);
        }
    }

    // --- Upstream failures

    @Test
    void keepsPostingsWhoseDetailFailedAsUnrated(CapturedOutput output) throws Exception {
        stubSearch("java developer", BRAZIL,
                card("701", "Desenvolvedor Júnior", "2026-10-01") + card("702", "Fullstack Developer", "2026-10-02"));
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "701")).willReturn(aResponse().withStatus(500)));
        stubDetail("702", detail("Fullstack Developer", null, "Entry level"));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"count": 2, "partial": true, "jobs": [
                          {"id": "702", "title": "Fullstack Developer", "company": "Acme", "location": "Brazil",
                           "url": "https://www.linkedin.com/jobs/view/702", "postedAt": "2026-10-02",
                           "track": "CORE", "seniority": "UNKNOWN", "descriptionAvailable": false,
                           "matchedSkills": [], "missingSkills": [], "category": "UNRATED"},
                          {"id": "701", "title": "Desenvolvedor Júnior", "company": "Acme", "location": "Brazil",
                           "url": "https://www.linkedin.com/jobs/view/701", "postedAt": "2026-10-01",
                           "track": "CORE", "seniority": "JUNIOR", "descriptionAvailable": false,
                           "matchedSkills": [], "missingSkills": [], "category": "UNRATED"}
                        ]}""", true));

        assertThat(warnLines(output)).singleElement().asString().contains("701").contains("500");
    }

    @Test
    void continuesWithTheSuccessfulSearchesWhenSomeFail(CapturedOutput output) throws Exception {
        linkedIn.stubFor(searchFor("kotlin", BRAZIL).willReturn(aResponse().withStatus(429)));
        stubSearch("java developer", BRAZIL, card("801", "Java Developer", null));
        stubDetail("801", detail("Java Developer", "Java", null));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.jobs[*].id").value(contains("801")));

        assertThat(searchesInOrder()).hasSize(4);
        assertThat(warnLines(output)).singleElement().asString()
                .contains("kotlin").contains("Brazil").contains("429");
    }

    @Test
    void answers502WhenEverySearchFails(CapturedOutput output) throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(429)));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("429")));

        assertThat(warnLines(output)).hasSize(4);
        linkedIn.verify(0, getRequestedFor(urlPathMatching(DETAIL_PATH + ".*")));
    }

    @Test
    void answers502NamingTheTimeoutWhenEverySearchTimesOut() throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH))
                .willReturn(html("").withFixedDelay(1500)));

        mockMvc.perform(get("/api/jobs/recommendations"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("timeout after 500 ms")));

        linkedIn.verify(0, getRequestedFor(urlPathMatching(DETAIL_PATH + ".*")));
    }

    // --- Helpers

    private static MappingBuilder searchFor(String keywords, String location) {
        return WireMock.get(urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("keywords", equalTo(keywords))
                .withQueryParam("location", equalTo(location));
    }

    private static void stubSearch(String keywords, String location, String cardsHtml) {
        linkedIn.stubFor(searchFor(keywords, location).willReturn(html(cardsHtml)));
    }

    private static void stubDetail(String id, String detailHtml) {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + id)).willReturn(html(detailHtml)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder html(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/html; charset=utf-8").withBody(body);
    }

    private static String card(String id, String title, String postedAt) {
        String time = postedAt == null ? "" : "<time datetime=\"" + postedAt + "\"></time>";
        return """
                <li><div class="base-card" data-entity-urn="urn:li:jobPosting:%s">
                  <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/%s?trk=x"></a>
                  <h3 class="base-search-card__title">%s</h3>
                  <h4 class="base-search-card__subtitle">Acme</h4>
                  <span class="job-search-card__location">Brazil</span>
                  %s
                </div></li>
                """.formatted(id, id, title, time);
    }

    /** {@code count} cards with ids {@code firstId..}; their details are unstubbed, so they come back UNRATED. */
    private static String cards(int firstId, int count) {
        StringBuilder html = new StringBuilder();
        for (int id = firstId; id < firstId + count; id++) {
            html.append(card(String.valueOf(id), "Dev " + id, null));
        }
        return html.toString();
    }

    private static String detail(String title, String description, String seniorityLevel) {
        String markup = description == null ? "" : """
                <div class="show-more-less-html__markup">%s</div>""".formatted(description);
        String criterion = seniorityLevel == null ? "" : """
                <ul><li class="description__job-criteria-item">
                  <h3 class="description__job-criteria-subheader">Seniority level</h3>
                  <span class="description__job-criteria-text">%s</span>
                </li></ul>""".formatted(seniorityLevel);
        return """
                <h2 class="top-card-layout__title">%s</h2>
                <a class="topcard__org-name-link" href="#">Acme</a>
                %s
                %s
                """.formatted(title, markup, criterion);
    }

    private static List<ServeEvent> servedInOrder() {
        return linkedIn.getAllServeEvents().stream()
                .sorted(Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .toList();
    }

    /** Each search as {@code keywords|location|f_TPR|f_WT|start}, with {@code -} for an absent parameter. */
    private static List<String> searchesInOrder() {
        return servedInOrder().stream()
                .filter(e -> e.getRequest().getUrl().startsWith(SEARCH_PATH))
                .map(e -> String.join("|",
                        param(e, "keywords"), param(e, "location"), param(e, "f_TPR"), param(e, "f_WT"),
                        param(e, "start")))
                .toList();
    }

    private static List<String> detailsInOrder() {
        return servedInOrder().stream()
                .map(e -> e.getRequest().getUrl())
                .filter(url -> url.startsWith(DETAIL_PATH))
                .map(url -> url.substring(DETAIL_PATH.length()))
                .toList();
    }

    private static String param(ServeEvent event, String name) {
        var parameter = event.getRequest().queryParameter(name);
        return parameter.isPresent() ? parameter.firstValue() : "-";
    }

    private static List<String> warnLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("LinkedIn")).toList();
    }
}
