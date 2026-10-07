package io.github.luccastk.jobsearch.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class TelegramClientTest {

    private static final String TOKEN = "123456:secret-test-token";
    private static final String SEND_PATH = "/bot" + TOKEN + "/sendMessage";

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
