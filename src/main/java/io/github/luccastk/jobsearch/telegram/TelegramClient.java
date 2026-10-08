package io.github.luccastk.jobsearch.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends messages through the Telegram Bot API. The bot token is part of every request URL, so no
 * exception or log line from here ever includes the URL or a Spring exception that quotes it.
 */
public class TelegramClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramClient(RestClient.Builder builder, TelegramProperties properties) {
        this.properties = properties;
        // HTTP/1.1: the JDK client's h2c upgrade attempt breaks POSTs against plain-HTTP servers (test stubs).
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    /**
     * Sends {@code htmlText} to the configured chat with {@code parse_mode=HTML} and link previews disabled.
     *
     * @throws TelegramException with the HTTP status or exception type when Telegram did not accept it
     */
    public void sendMessage(String htmlText) {
        Map<String, Object> body = Map.of(
                "chat_id", properties.chatId(),
                "text", htmlText,
                "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true));
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(sendMessageUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, resp) -> {
                        throw new RestClientResponseException("Non-2xx response", resp.getStatusCode(),
                                resp.getStatusText(), resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new TelegramException("Telegram responded with HTTP " + e.getStatusCode().value()
                    + description(e.getResponseBodyAsByteArray()));
        } catch (RestClientException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new TelegramException("Telegram request failed: " + cause.getClass().getSimpleName());
        }
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new TelegramException("Telegram responded with HTTP 200 but ok=false");
        }
    }

    /** Telegram's {@code description} (e.g. "Bad Request: can't parse entities") as a ": ..." suffix, or "". */
    private static String description(byte[] body) {
        try {
            JsonNode description = JSON.readTree(body).path("description");
            return description.isTextual() ? ": " + description.asText() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Component-level encoding keeps the ':' of {@code <id>:<secret>} tokens literal in the path. */
    private URI sendMessageUri() {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/bot{token}/sendMessage")
                .buildAndExpand(properties.botToken())
                .encode()
                .toUri();
    }
}
