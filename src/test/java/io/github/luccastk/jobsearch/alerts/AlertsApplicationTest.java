package io.github.luccastk.jobsearch.alerts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** The whole application with alerts enabled; LinkedIn and Telegram are WireMock stubs. */
@SpringBootTest
@AutoConfigureMockMvc
class AlertsApplicationTest {

    private static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String TOKEN = "123456:application-test-token";

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .resetOnEachTest(false)
            .build();

    @RegisterExtension
    static WireMockExtension telegram = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .resetOnEachTest(false)
            .build();

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("linkedin.base-url", linkedIn::baseUrl);
        registry.add("linkedin.page-delay", () -> "50ms");
        registry.add("telegram.base-url", telegram::baseUrl);
        registry.add("telegram.bot-token", () -> TOKEN);
        registry.add("telegram.chat-id", () -> "987654");
        registry.add("alerts.enabled", () -> "true");
        registry.add("alerts.interval", () -> "1h");
        registry.add("alerts.db-path", () -> dataDir.resolve("db/jobs.db").toString());
        registry.add("alerts.searches[0].keywords", () -> "java");
    }

    /** Stubs exist before the context starts, because the first cycle runs right at startup. */
    @BeforeAll
    static void stubUpstreams() {
        stubFirstPage("java", cards(1, 2));
        stubFirstPage("kotlin", cards(10, 1));
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).atPriority(9)
                .willReturn(aResponse().withStatus(200).withBody("")));
        telegram.stubFor(post(anyUrl()).willReturn(okJson("{\"ok\": true, \"result\": {}}")));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SeenJobStore store;

    @Test
    void runsAFirstSeedingCycleRightAtStartup() {
        awaitStartupCycle();

        assertThat(dataDir.resolve("db/jobs.db")).exists();
        assertThat(store.contains("1")).isTrue();
        assertThat(store.contains("2")).isTrue();
        telegram.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void restSearchNeitherReadsNorWritesTheSeenJobsStore() throws Exception {
        awaitStartupCycle();
        clearInvocations(store);

        mockMvc.perform(get("/api/jobs/search").param("keywords", "kotlin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].id").value("10"));

        verifyNoInteractions(store);
        assertThat(store.contains("10")).isFalse();
    }

    private void awaitStartupCycle() {
        await().atMost(Duration.ofSeconds(10)).until(() -> store.contains("2"));
    }

    private static void stubFirstPage(String keywords, String html) {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(SEARCH_PATH)).atPriority(1)
                .withQueryParam("keywords", equalTo(keywords))
                .withQueryParam("start", equalTo("0"))
                .willReturn(aResponse().withStatus(200).withBody(html)));
    }

    private static String cards(int firstId, int count) {
        StringBuilder html = new StringBuilder();
        for (int id = firstId; id < firstId + count; id++) {
            html.append("""
                    <li><div class="base-card" data-entity-urn="urn:li:jobPosting:%d">
                      <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/%d"></a>
                      <h3 class="base-search-card__title">Job %d</h3>
                    </div></li>
                    """.formatted(id, id, id));
        }
        return html.toString();
    }
}
