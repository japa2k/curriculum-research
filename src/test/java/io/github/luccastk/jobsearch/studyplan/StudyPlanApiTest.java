package io.github.luccastk.jobsearch.studyplan;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The CLI is {@link FakeClaude} in echo mode, so the plan it "generates" is the prompt it received. */
@SpringBootTest
@AutoConfigureMockMvc
class StudyPlanApiTest {

    private static final String DETAIL_PATH = "/jobs-guest/jobs/api/jobPosting/";

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("linkedin.base-url", linkedIn::baseUrl);
        registry.add("linkedin.timeout", () -> "500ms");
        registry.add("profile.location", () -> "classpath:profiles/recommendations.yml");
        registry.add("alerts.enabled", () -> "false");
        List<String> command = FakeClaude.command("echo");
        registry.add("claude-cli.command", command::getFirst);
        for (int i = 1; i < command.size(); i++) {
            String arg = command.get(i);
            registry.add("claude-cli.args[" + (i - 1) + "]", () -> arg);
        }
        registry.add("claude-cli.timeout", () -> "20s");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ClaudeCli cli;

    @Test
    void returnsAStudyPlanForThePosting() throws Exception {
        stubDetail("4242", detail("Backend Developer", "Java, Kotlin and AWS. Written in English.", "Entry level"));

        MvcResult result = mockMvc.perform(get("/api/jobs/4242/study-plan"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value("4242"))
                .andExpect(jsonPath("$.title").value("Backend Developer"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.url").value("https://www.linkedin.com/jobs/view/4242"))
                .andExpect(jsonPath("$.seniority").value("JUNIOR"))
                .andExpect(jsonPath("$.matchedSkills").value(contains("Java")))
                .andExpect(jsonPath("$.missingSkills").value(contains("AWS", "Kotlin")))
                .andExpect(jsonPath("$.weeklyHours").value(10))
                .andExpect(jsonPath("$.maxWeeks").value(8))
                .andReturn();

        verify(cli, times(1)).run(anyString());
        linkedIn.verify(1, getRequestedFor(urlPathEqualTo(DETAIL_PATH + "4242")));
        String plan = JsonPath.read(result.getResponse().getContentAsString(), "$.plan");
        assertThat(plan).isEqualTo(plan.trim()).startsWith("ARGS:");
    }

    @Test
    void sendsThePostingProfileAndBudgetInThePromptThroughStdin() throws Exception {
        stubDetail("4242", detail("Backend Developer", "Java, Kotlin and AWS. Written in English.", "Entry level"));

        MvcResult result = mockMvc.perform(get("/api/jobs/4242/study-plan")).andExpect(status().isOk()).andReturn();

        String plan = JsonPath.read(result.getResponse().getContentAsString(), "$.plan");
        String args = plan.substring(0, plan.indexOf("\nSTDIN:"));
        String prompt = plan.substring(plan.indexOf("\nSTDIN:") + "\nSTDIN:\n".length());
        assertThat(args).doesNotContain("Backend Developer").doesNotContain("Kotlin");
        assertThat(prompt)
                .contains("Backend Developer")
                .contains("Acme")
                .contains("Java, Kotlin and AWS. Written in English.")
                .contains("Java, Node.js, NestJS, React, TypeScript, SQL")
                .contains("AWS, Kotlin")
                .contains("10 horas por semana")
                .contains("8 semanas")
                .contains("Markdown")
                .contains("português do Brasil")
                .contains("projeto prático")
                .contains("inglês");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12a", "123456789012345678901", "-1"})
    void rejectsInvalidIdsWithoutCallingLinkedInOrTheCli(String id) throws Exception {
        mockMvc.perform(get("/api/jobs/" + id + "/study-plan"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("id")));

        linkedIn.verify(0, anyRequestedFor(anyUrl()));
        verify(cli, never()).run(anyString());
    }

    @Test
    void acceptsTwentyDigitIds() throws Exception {
        String id = "12345678901234567890";
        stubDetail(id, detail("Dev", "Java", null));

        mockMvc.perform(get("/api/jobs/" + id + "/study-plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(id));
    }

    @Test
    void answers404WhenLinkedInHasNoSuchPosting() throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "404")).willReturn(aResponse().withStatus(404)));

        mockMvc.perform(get("/api/jobs/404/study-plan"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(containsString("404")));

        verify(cli, never()).run(anyString());
    }

    @Test
    void answers502WhenTheDetailRequestFails() throws Exception {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "500")).willReturn(aResponse().withStatus(500)));

        mockMvc.perform(get("/api/jobs/500/study-plan"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("500")));

        verify(cli, never()).run(anyString());
    }

    @Test
    void answers502WhenThePageHasNoDescription() throws Exception {
        stubDetail("777", detail("Dev", null, "Entry level"));

        mockMvc.perform(get("/api/jobs/777/study-plan"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(containsString("description")));

        verify(cli, never()).run(anyString());
    }

    @Test
    void answers503WhenTheCliFails() throws Exception {
        stubDetail("888", detail("Dev", "Java", null));
        doThrow(new StudyPlanGenerationException("Claude CLI timed out after 180 s")).when(cli).run(anyString());

        mockMvc.perform(get("/api/jobs/888/study-plan"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"error": "Claude CLI timed out after 180 s"}""", true));
    }

    private static void stubDetail(String id, String html) {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + id)).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "text/html; charset=utf-8").withBody(html)));
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
}
