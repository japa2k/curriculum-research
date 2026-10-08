package io.github.luccastk.jobsearch.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class TelegramClientTest {

    private static final String TOKEN = "123456:secret-test-token";
    private static final String SEND_PATH = "/bot" + TOKEN + "/sendMessage";
    private static final String UPDATES_PATH = "/bot" + TOKEN + "/getUpdates";
    private static final String ANSWER_PATH = "/bot" + TOKEN + "/answerCallbackQuery";
    private static final String DOCUMENT_PATH = "/bot" + TOKEN + "/sendDocument";

    @RegisterExtension
    static WireMockExtension telegram = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private TelegramClient client;

    @BeforeEach
    void setUp() {
        TelegramProperties properties =
                new TelegramProperties(telegram.baseUrl(), TOKEN, "987654", Duration.ofMillis(500));
        client = new TelegramClient(RestClient.builder(), properties);
    }

    @Test
    void postsTheTextAsHtmlToTheConfiguredChatWithLinkPreviewsDisabled() {
        stubSend(okJson("{\"ok\": true, \"result\": {}}"));

        client.sendMessage("<b>Java & more</b>");

        telegram.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"chat_id": "987654", "text": "<b>Java & more</b>", "parse_mode": "HTML",
                         "link_preview_options": {"is_disabled": true}}""")));
    }

    @Test
    void attachesAnInlineKeyboardWithOneButtonWhenGivenOne() {
        stubSend(okJson("{\"ok\": true, \"result\": {}}"));

        client.sendMessage("<b>Job</b>", new InlineButton("📄 Gerar currículo", "resume:4242"));

        telegram.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH))
                .withRequestBody(equalToJson("""
                        {"chat_id": "987654", "text": "<b>Job</b>", "parse_mode": "HTML",
                         "link_preview_options": {"is_disabled": true},
                         "reply_markup": {"inline_keyboard": [[
                           {"text": "📄 Gerar currículo", "callback_data": "resume:4242"}]]}}""")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500})
    void failsWithTheHttpStatusOnANon2xxResponse(int status) {
        stubSend(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"ok\": false, \"error_code\": " + status + ", \"description\": \"nope\"}"));

        assertThatThrownBy(() -> client.sendMessage("hi"))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("HTTP " + status)
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    @Test
    void includesTelegramsErrorDescriptionOnANon2xxResponse() {
        stubSend(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"ok\": false, \"error_code\": 400, "
                        + "\"description\": \"Bad Request: can't parse entities\"}"));

        assertThatThrownBy(() -> client.sendMessage("hi"))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("Bad Request: can't parse entities")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void failsWhenTelegramAnswersOkFalse() {
        stubSend(okJson("{\"ok\": false, \"description\": \"Bad Request\"}"));

        assertThatThrownBy(() -> client.sendMessage("hi"))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("HTTP 200")
                .hasMessageContaining("ok=false");
    }

    @Test
    void failsWithTheExceptionTypeOnTimeout() {
        stubSend(okJson("{\"ok\": true}").withFixedDelay(1500));

        assertThatThrownBy(() -> client.sendMessage("hi"))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("Timeout")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    @Test
    void failsWithTheExceptionTypeOnConnectionError() {
        stubSend(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        assertThatThrownBy(() -> client.sendMessage("hi"))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("Exception")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    // --- getUpdates

    @Test
    void longPollsForCallbackQueriesFromTheGivenOffset() {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH)).willReturn(okJson("""
                {"ok": true, "result": [
                  {"update_id": 7, "callback_query": {"id": "cb-1", "data": "resume:4242",
                    "from": {"id": 1}, "message": {"message_id": 3, "chat": {"id": 987654}}}},
                  {"update_id": 8, "message": {"message_id": 4, "chat": {"id": 987654}, "text": "oi"}},
                  {"update_id": 9, "callback_query": {"id": "cb-2", "from": {"id": 1}}}
                ]}""")));

        List<TelegramUpdate> updates = client.getUpdates(7, Duration.ofSeconds(25));

        telegram.verify(1, postRequestedFor(urlPathEqualTo(UPDATES_PATH))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"offset": 7, "timeout": 25, "allowed_updates": ["callback_query"]}""")));
        assertThat(updates).containsExactly(
                new TelegramUpdate(7, new CallbackQuery("cb-1", "987654", "resume:4242")),
                new TelegramUpdate(8, null),
                new TelegramUpdate(9, new CallbackQuery("cb-2", null, null)));
    }

    @Test
    void waitsForTheLongPollBeyondTheRequestTimeout() {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH))
                .willReturn(okJson("{\"ok\": true, \"result\": []}").withFixedDelay(1000)));

        assertThat(client.getUpdates(0, Duration.ofSeconds(1))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"500", "ok=false", "timeout", "reset"})
    void getUpdatesFailsWithoutTheToken(String failure) {
        telegram.stubFor(post(urlPathEqualTo(UPDATES_PATH)).willReturn(switch (failure) {
            case "500" -> aResponse().withStatus(500);
            case "ok=false" -> okJson("{\"ok\": false}");
            case "timeout" -> okJson("{\"ok\": true, \"result\": []}").withFixedDelay(1500);
            default -> aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
        }));

        assertThatThrownBy(() -> client.getUpdates(0, Duration.ZERO))
                .isInstanceOf(TelegramException.class)
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    // --- answerCallbackQuery

    @Test
    void answersACallbackQueryWithAnOptionalText() {
        telegram.stubFor(post(urlPathEqualTo(ANSWER_PATH)).willReturn(okJson("{\"ok\": true, \"result\": true}")));

        client.answerCallbackQuery("cb-1", "Botão inválido.");
        client.answerCallbackQuery("cb-2", null);

        telegram.verify(1, postRequestedFor(urlPathEqualTo(ANSWER_PATH))
                .withRequestBody(equalToJson("""
                        {"callback_query_id": "cb-1", "text": "Botão inválido."}""")));
        telegram.verify(1, postRequestedFor(urlPathEqualTo(ANSWER_PATH))
                .withRequestBody(equalToJson("""
                        {"callback_query_id": "cb-2"}""")));
    }

    @Test
    void answerCallbackQueryFailsWithoutTheToken() {
        telegram.stubFor(post(urlPathEqualTo(ANSWER_PATH)).willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> client.answerCallbackQuery("cb-1", null))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    // --- sendDocument

    @Test
    void uploadsADocumentWithItsFileNameAndCaptionToTheConfiguredChat() {
        telegram.stubFor(post(urlPathEqualTo(DOCUMENT_PATH)).willReturn(okJson("{\"ok\": true, \"result\": {}}")));
        byte[] content = "conteúdo do arquivo".getBytes(StandardCharsets.UTF_8);

        client.sendDocument("curriculo-acme-4242.docx", content, "Dev — Acme\nhttps://example.com/4242");

        telegram.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENT_PATH))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBodyPart(aMultipart("chat_id").withBody(equalTo("987654")).build())
                .withRequestBodyPart(aMultipart("caption")
                        .withBody(equalTo("Dev — Acme\nhttps://example.com/4242")).build())
                .withRequestBodyPart(aMultipart("document")
                        .withHeader("Content-Disposition", containing("filename=\"curriculo-acme-4242.docx\""))
                        .withBody(binaryEqualTo(content)).build()));
    }

    @Test
    void sendDocumentFailsWithoutTheToken() {
        telegram.stubFor(post(urlPathEqualTo(DOCUMENT_PATH)).willReturn(aResponse().withStatus(413)));

        assertThatThrownBy(() -> client.sendDocument("projeto.md", new byte[] {1}, null))
                .isInstanceOf(TelegramException.class)
                .hasMessageContaining("HTTP 413")
                .hasMessageNotContaining(TOKEN)
                .hasNoCause();
    }

    @Test
    void propertiesNeverPrintTheToken() {
        TelegramProperties properties =
                new TelegramProperties("https://api.telegram.org", TOKEN, "987654", Duration.ofSeconds(10));

        assertThat(properties.toString()).doesNotContain(TOKEN).contains("987654");
    }

    private static void stubSend(ResponseDefinitionBuilder response) {
        telegram.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(response));
    }
}
