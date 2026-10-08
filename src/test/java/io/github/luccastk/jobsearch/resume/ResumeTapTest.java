package io.github.luccastk.jobsearch.resume;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.luccastk.jobsearch.studyplan.ClaudeCli;
import io.github.luccastk.jobsearch.studyplan.ClaudeCliBusyException;
import io.github.luccastk.jobsearch.studyplan.FakeClaude;
import io.github.luccastk.jobsearch.telegram.CallbackQuery;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A button press, from the handler through LinkedIn, the CLI, the disk, SQLite and Telegram. LinkedIn and
 * Telegram are WireMock stubs and the CLI is {@link FakeClaude} in {@code documents} mode, steered per test by
 * a directive in the posting's description.
 */
@SpringBootTest
// Closes the context afterwards, so its Telegram poller does not outlive the WireMock servers.
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class ResumeTapTest {

    private static final String TOKEN = "123456:tap-test-token";
    private static final String CHAT_ID = "987654";
    private static final String DETAIL_PATH = "/jobs-guest/jobs/api/jobPosting/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger CALLBACK_IDS = new AtomicInteger();

    @RegisterExtension
    static WireMockExtension linkedIn = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
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
        registry.add("profile.location", () -> "classpath:profiles/recommendations.yml");
        registry.add("telegram.base-url", telegram::baseUrl);
        registry.add("telegram.bot-token", () -> TOKEN);
        registry.add("telegram.chat-id", () -> CHAT_ID);
        registry.add("alerts.enabled", () -> "true");
        registry.add("alerts.db-path", () -> dataDir.resolve("jobs.db").toString());
        registry.add("alerts.searches[0].keywords", () -> "java");
        registry.add("resume.base-path", ResumeTapTest::baseResumeFixture);
        registry.add("resume.applications-dir", () -> applicationsDir().toString());
        List<String> command = FakeClaude.command("documents");
        registry.add("claude-cli.command", command::getFirst);
        for (int i = 1; i < command.size(); i++) {
            String arg = command.get(i);
            registry.add("claude-cli.args[" + (i - 1) + "]", () -> arg);
        }
        registry.add("claude-cli.timeout", () -> "8s");
    }

    @Autowired
    private ResumeTapHandler handler;

    @MockitoSpyBean
    private ApplicationStore store;

    @MockitoSpyBean
    private ClaudeCli cli;

    @BeforeEach
    void stubTelegram() {
        telegram.resetAll();
        // The poller long-polls throughout; with nothing to deliver, Telegram holds each poll briefly.
        telegram.stubFor(post(urlPathEqualTo(path("getUpdates"))).atPriority(9)
                .willReturn(okJson("{\"ok\": true, \"result\": []}").withFixedDelay(100)));
        telegram.stubFor(post(anyUrl()).atPriority(10).willReturn(okJson("{\"ok\": true, \"result\": {}}")));
    }

    // --- Generating

    @Test
    void generatesSavesAndSendsTheResumeStudyPlanAndProject(CapturedOutput output) throws IOException {
        stubDetail("4242", "Backend Developer", "Acme", "Java and AWS.");

        tap("4242");
        awaitDocuments(3);

        assertThat(messages()).containsExactly(
                "Gerando currículo, plano de estudos e projeto para Backend Developer — Acme…");
        assertThat(documents()).extracting(Document::fileName)
                .containsExactly("curriculo-acme-4242.docx", "plano-de-estudos.md", "projeto.md");
        assertThat(documents().getFirst().caption())
                .isEqualTo("Backend Developer — Acme\nhttps://www.linkedin.com/jobs/view/4242");

        Path jobDir = applicationsDir().resolve("4242");
        assertThat(docxText(Files.readAllBytes(jobDir.resolve("curriculo.docx"))))
                .contains("Fulano Teste")
                .contains("fulano.teste@example.com | (11) 90000-0000 | São Paulo, SP")
                .contains("• Projeto Fake (2026)");
        assertThat(jobDir.resolve("plano-de-estudos.md")).content(StandardCharsets.UTF_8).contains("# Plano Fake");
        assertThat(jobDir.resolve("projeto.md")).content(StandardCharsets.UTF_8).contains("# Projeto Fake");
        assertThat(documents().getFirst().content()).isEqualTo(Files.readAllBytes(jobDir.resolve("curriculo.docx")));

        assertThat(store.find("4242")).hasValueSatisfying(application -> {
            assertThat(application.title()).isEqualTo("Backend Developer");
            assertThat(application.company()).isEqualTo("Acme");
            assertThat(application.url()).isEqualTo("https://www.linkedin.com/jobs/view/4242");
            assertThat(application.generatedAt()).isNotNull();
        });
        assertThat(output.getAll()).doesNotContain("fulano.teste@example.com").doesNotContain(TOKEN);
    }

    @Test
    void datesTheProjectWithTheCurrentYearInThePrompt() {
        stubDetail("8282", "Dev", "Acme", "Java.");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);

        tap("8282");
        awaitDocuments(3);

        verify(cli).run(prompt.capture());
        assertThat(prompt.getValue()).contains("apenas o ano " + Year.now().getValue())
                .contains("<curriculo_base>\n# Fulano Teste");
    }

    @Test
    void answersTheButtonRightAwayAndGeneratesOffTheCallersThread() {
        stubDetail("5151", "Dev", "Acme", "Java. FAKE_SLOW");

        long started = System.nanoTime();
        String callbackId = tap("5151");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        telegram.verify(1, postRequestedFor(urlPathEqualTo(path("answerCallbackQuery")))
                .withRequestBody(equalToJson("{\"callback_query_id\": \"" + callbackId + "\"}")));
        awaitDocuments(3);
    }

    @Test
    void escapesTheTitleAndCompanyInTheProgressMessage() {
        stubDetail("6161", "C++ & <Go> Dev", "Ácme <Labs>", "Java.");

        tap("6161");
        awaitDocuments(3);

        assertThat(lastSendMessageBody().path("parse_mode").asText()).isEqualTo("HTML");
        assertThat(messages()).containsExactly(
                "Gerando currículo, plano de estudos e projeto para C++ &amp; &lt;Go&gt; Dev — Ácme &lt;Labs&gt;…");
        assertThat(documents().getFirst().fileName()).isEqualTo("curriculo-acme-labs-6161.docx");
    }

    @Test
    void fallsBackToTheJobIdWhenThePostingHasNoTitleOrCompany() {
        stubDetail("7171", null, null, "Java.");

        tap("7171");
        awaitDocuments(3);

        assertThat(messages()).containsExactly("Gerando currículo, plano de estudos e projeto para 7171…");
        assertThat(documents().getFirst().fileName()).isEqualTo("curriculo-7171.docx");
    }

    // --- Saved applications

    @Test
    void resendsTheSavedFilesWithoutRunningTheCliOrFetchingLinkedIn() {
        stubDetail("8181", "Dev", "Acme", "Java.");
        tap("8181");
        awaitDocuments(3);
        List<Document> first = documents();
        resetJournals();

        tap("8181");
        awaitDocuments(3);

        verify(cli, never()).run(anyString());
        linkedIn.verify(0, anyRequestedFor(anyUrl()));
        assertThat(documents()).usingRecursiveComparison().isEqualTo(first);
    }

    @Test
    void regeneratesWhenASavedFileIsMissing() throws IOException {
        stubDetail("9191", "Dev", "Acme", "Java.");
        tap("9191");
        awaitDocuments(3);
        Files.delete(applicationsDir().resolve("9191/projeto.md"));
        resetJournals();

        tap("9191");
        awaitDocuments(3);

        verify(cli, times(1)).run(anyString());
        assertThat(applicationsDir().resolve("9191/projeto.md")).exists();
    }

    @Test
    void refusesASecondGenerationForTheSameJobWhileTheFirstRuns() {
        stubDetail("1212", "Dev", "Acme", "Java. FAKE_SLOW");

        tap("1212");
        await().atMost(Duration.ofSeconds(5)).until(() -> messages().size() == 1);
        tap("1212");
        awaitDocuments(3);

        assertThat(messages()).containsExactly(
                "Gerando currículo, plano de estudos e projeto para Dev — Acme…",
                "Já estou gerando esse currículo, aguarde.");
        verify(cli, times(1)).run(anyString());
        telegram.verify(2, postRequestedFor(urlPathEqualTo(path("answerCallbackQuery"))));
    }

    // --- Invalid presses

    @ParameterizedTest
    @ValueSource(strings = {"nope", "resume:abc"})
    void answersAnInvalidButtonAndDoesNothingElse(String data) {
        handler.accept(new CallbackQuery("cb-invalid", CHAT_ID, data));

        telegram.verify(1, postRequestedFor(urlPathEqualTo(path("answerCallbackQuery")))
                .withRequestBody(equalToJson("""
                        {"callback_query_id": "cb-invalid", "text": "Botão inválido."}""")));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(() -> messages().isEmpty());
        linkedIn.verify(0, anyRequestedFor(anyUrl()));
        verify(cli, never()).run(anyString());
    }

    // --- Failures

    @Test
    void repliesThatThePostingIsGoneWhenLinkedInAnswers404() {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "404")).willReturn(aResponse().withStatus(404)));

        tap("404");

        awaitReply("Essa vaga não está mais disponível no LinkedIn.");
        assertNothingSaved("404");
        verify(cli, never()).run(anyString());
    }

    @Test
    void repliesThatThePostingCannotBeReadWhenLinkedInFails() {
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + "500")).willReturn(aResponse().withStatus(500)));

        tap("500");

        awaitReply("Não consegui ler a vaga no LinkedIn agora. Tente de novo mais tarde.");
        assertNothingSaved("500");
    }

    @Test
    void repliesThatThePostingCannotBeReadWhenThePageHasNoDescription() {
        stubDetail("777", "Dev", "Acme", null);

        tap("777");

        awaitReply("Não consegui ler a vaga no LinkedIn agora. Tente de novo mais tarde.");
        assertNothingSaved("777");
        verify(cli, never()).run(anyString());
    }

    @Test
    void repliesBusyWhenEveryCliSlotIsTaken() {
        stubDetail("3131", "Dev", "Acme", "Java.");
        doThrow(new ClaudeCliBusyException("Claude CLI is busy")).when(cli).run(anyString());

        tap("3131");

        awaitReply("Não consegui gerar agora (ocupado). Clique de novo mais tarde.");
        assertNothingSaved("3131");
    }

    @ParameterizedTest
    @CsvSource({"FAKE_FAIL, 4001", "FAKE_EMPTY, 4002", "FAKE_GARBAGE, 4003", "FAKE_SLEEP, 4004"})
    void repliesGenerationFailedWhenTheCliFailsTimesOutPrintsNothingOrCannotBeSplit(String directive, String jobId) {
        stubDetail(jobId, "Dev", "Acme", "Java. " + directive);

        tap(jobId);

        await().atMost(Duration.ofSeconds(20)).until(() -> messages().contains(
                "Não consegui gerar agora (falha na geração). Clique de novo mais tarde."));
        awaitIdle();
        assertNothingSaved(jobId);
    }

    @Test
    void repliesThatTheFilesCannotBeSavedAndRecordsNothing(CapturedOutput output) throws IOException {
        stubDetail("5252", "Dev", "Acme", "Java.");
        Files.createDirectories(applicationsDir());
        Files.writeString(applicationsDir().resolve("5252"), "a file where the job's directory should be");

        tap("5252");

        awaitReply("Não consegui salvar os arquivos.");
        assertThat(store.find("5252")).isEmpty();
        telegram.verify(0, postRequestedFor(urlPathEqualTo(path("sendDocument"))));
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("5252")))
                .isNotEmpty();
    }

    @Test
    void repliesThatTheFilesCannotBeSavedWhenRecordingTheApplicationFails(CapturedOutput output) {
        stubDetail("7373", "Dev", "Acme", "Java.");
        doThrow(new DataAccessResourceFailureException("database is locked")).when(store).save(any());

        tap("7373");

        awaitReply("Não consegui salvar os arquivos.");
        telegram.verify(0, postRequestedFor(urlPathEqualTo(path("sendDocument"))));
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("7373")))
                .isNotEmpty();
    }

    @Test
    void keepsTheSavedFilesWhenSendingADocumentFailsSoTheNextPressResendsThem(CapturedOutput output) {
        stubDetail("6262", "Dev", "Acme", "Java.");
        telegram.stubFor(post(urlPathEqualTo(path("sendDocument"))).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        tap("6262");

        await().atMost(Duration.ofSeconds(15)).until(() -> output.getOut().lines()
                .anyMatch(line -> line.contains("WARN") && line.contains("sendDocument") && line.contains("6262")));
        awaitIdle();
        assertThat(store.find("6262")).isPresent();
        assertThat(applicationsDir().resolve("6262/curriculo.docx")).exists();

        stubTelegram();
        clearInvocations(cli);
        tap("6262");
        awaitDocuments(3);
        verify(cli, never()).run(anyString());
    }

    // --- Through Telegram

    @Test
    void handlesAPressDeliveredByGetUpdates() {
        stubDetail("7272", "Dev", "Acme", "Java.");
        telegram.stubFor(post(urlPathEqualTo(path("getUpdates"))).atPriority(1)
                .inScenario("press").whenScenarioStateIs(STARTED).willSetStateTo("delivered")
                .willReturn(okJson("""
                        {"ok": true, "result": [{"update_id": 900, "callback_query": {"id": "cb-polled",
                          "from": {"id": 1}, "data": "resume:7272",
                          "message": {"message_id": 5, "chat": {"id": 987654}}}}]}""")));

        awaitDocuments(3);

        telegram.verify(1, postRequestedFor(urlPathEqualTo(path("answerCallbackQuery")))
                .withRequestBody(equalToJson("{\"callback_query_id\": \"cb-polled\"}")));
        assertThat(documents().getFirst().fileName()).isEqualTo("curriculo-acme-7272.docx");
    }

    // --- Helpers

    private String tap(String jobId) {
        String callbackId = "cb-" + CALLBACK_IDS.incrementAndGet();
        handler.accept(new CallbackQuery(callbackId, CHAT_ID, "resume:" + jobId));
        return callbackId;
    }

    private void assertNothingSaved(String jobId) {
        assertThat(applicationsDir().resolve(jobId)).doesNotExist();
        assertThat(store.find(jobId)).isEmpty();
        telegram.verify(0, postRequestedFor(urlPathEqualTo(path("sendDocument"))));
    }

    private void resetJournals() {
        telegram.resetRequests();
        linkedIn.resetRequests();
        clearInvocations(cli);
    }

    private void awaitReply(String text) {
        await().atMost(Duration.ofSeconds(15)).until(() -> messages().contains(text));
        awaitIdle();
    }

    private void awaitDocuments(int count) {
        await().atMost(Duration.ofSeconds(15)).until(() -> documents().size() >= count);
        awaitIdle();
    }

    /** Every generation has finished, so the next press of the same job is not refused as a duplicate. */
    private void awaitIdle() {
        await().atMost(Duration.ofSeconds(15)).until(handler::idle);
    }

    private static void stubDetail(String id, String title, String company, String description) {
        String titleMarkup = title == null ? "" : "<h2 class=\"top-card-layout__title\">%s</h2>"
                .formatted(escapeHtml(title));
        String companyMarkup = company == null ? "" : "<a class=\"topcard__org-name-link\" href=\"#\">%s</a>"
                .formatted(escapeHtml(company));
        String descriptionMarkup = description == null ? "" : "<div class=\"show-more-less-html__markup\">%s</div>"
                .formatted(description);
        linkedIn.stubFor(WireMock.get(urlPathEqualTo(DETAIL_PATH + id)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody(titleMarkup + companyMarkup + descriptionMarkup)));
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Telegram's requests to {@code method}, oldest first. */
    private static List<LoggedRequest> requests(String method) {
        List<LoggedRequest> requests = new ArrayList<>(telegram.findAll(postRequestedFor(urlPathEqualTo(path(method)))));
        requests.sort((a, b) -> a.getLoggedDate().compareTo(b.getLoggedDate()));
        return requests;
    }

    private static List<String> messages() {
        return requests("sendMessage").stream().map(request -> json(request).path("text").asText()).toList();
    }

    private static JsonNode lastSendMessageBody() {
        return json(requests("sendMessage").getLast());
    }

    private record Document(String fileName, String caption, byte[] content) {
    }

    private static List<Document> documents() {
        List<Document> documents = new ArrayList<>();
        for (LoggedRequest request : requests("sendDocument")) {
            var document = request.getPart("document");
            var caption = request.getPart("caption");
            String disposition = document.getHeader("Content-Disposition").firstValue();
            String fileName = disposition.replaceAll(".*filename=\"([^\"]+)\".*", "$1");
            documents.add(new Document(fileName, caption == null ? null : caption.getBody().asString(),
                    document.getBody().asBytes()));
        }
        return Collections.unmodifiableList(documents);
    }

    private static JsonNode json(LoggedRequest request) {
        try {
            return JSON.readTree(request.getBodyAsString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String docxText(byte[] docx) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String path(String method) {
        return "/bot" + TOKEN + "/" + method;
    }

    private static Path applicationsDir() {
        return dataDir.resolve("applications");
    }

    private static String baseResumeFixture() {
        try {
            return Path.of(ResumeTapTest.class.getResource("/resume/resume-base.md").toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
