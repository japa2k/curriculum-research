package io.github.luccastk.jobsearch.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the Telegram Bot API. The bot token is part of every request URL, so no exception or log line from
 * here ever includes the URL or a Spring exception that quotes it.
 */
public class TelegramClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TelegramProperties properties;
    private final HttpClient httpClient;
    private final RestClient restClient;

    public TelegramClient(RestClient.Builder builder, TelegramProperties properties) {
        this.properties = properties;
        // HTTP/1.1: the JDK client's h2c upgrade attempt breaks POSTs against plain-HTTP servers (test stubs).
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        this.restClient = builder.requestFactory(requestFactory(properties.timeout())).build();
    }

    /**
     * Sends {@code htmlText} to the configured chat with {@code parse_mode=HTML} and link previews disabled.
     *
     * @throws TelegramException with the HTTP status or exception type when Telegram did not accept it
     */
    public void sendMessage(String htmlText) {
        sendMessage(htmlText, null);
    }

    /**
     * Like {@link #sendMessage(String)}, with an inline keyboard holding just {@code button}.
     *
     * @param button the keyboard's only button, or {@code null} for none
     */
    public void sendMessage(String htmlText, InlineButton button) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", properties.chatId());
        body.put("text", htmlText);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        if (button != null) {
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(
                    Map.of("text", button.text(), "callback_data", button.callbackData())))));
        }
        post(restClient, "sendMessage", MediaType.APPLICATION_JSON, body);
    }

    /**
     * Waits up to {@code longPoll} for callback queries with an update id of at least {@code offset}; asking
     * with a higher offset confirms every earlier update, so Telegram never returns it again.
     *
     * @return the updates in Telegram's order; other kinds of update have a {@code null} callback query
     * @throws TelegramException with the HTTP status or exception type when the call failed
     */
    public List<TelegramUpdate> getUpdates(long offset, Duration longPoll) {
        // Telegram may hold the answer for the whole long poll, so this request's read timeout extends past it.
        RestClient pollClient = restClient.mutate()
                .requestFactory(requestFactory(properties.timeout().plus(longPoll)))
                .build();
        Map<String, Object> body = Map.of(
                "offset", offset,
                "timeout", longPoll.toSeconds(),
                "allowed_updates", List.of("callback_query"));
        List<TelegramUpdate> updates = new ArrayList<>();
        for (JsonNode update : post(pollClient, "getUpdates", MediaType.APPLICATION_JSON, body).path("result")) {
            JsonNode query = update.path("callback_query");
            CallbackQuery callbackQuery = query.isObject()
                    ? new CallbackQuery(query.path("id").asText(),
                            textOrNull(query.path("message").path("chat").path("id")),
                            textOrNull(query.path("data")))
                    : null;
            updates.add(new TelegramUpdate(update.path("update_id").asLong(), callbackQuery));
        }
        return updates;
    }

    /**
     * Stops the pressed button's loading spinner.
     *
     * @param text a short notice Telegram shows the user, or {@code null} for none
     */
    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null) {
            body.put("text", text);
        }
        post(restClient, "answerCallbackQuery", MediaType.APPLICATION_JSON, body);
    }

    /**
     * Uploads {@code content} as a file named {@code fileName} to the configured chat.
     *
     * @param caption shown under the file as plain text, or {@code null} for none
     */
    public void sendDocument(String fileName, byte[] content, String caption) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", properties.chatId());
        if (caption != null) {
            // Spring writes plain String parts as ISO-8859-1; the caption may hold any character.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
            body.add("caption", new HttpEntity<>(caption, headers));
        }
        body.add("document", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        post(restClient, "sendDocument", MediaType.MULTIPART_FORM_DATA, body);
    }

    private JsonNode post(RestClient client, String method, MediaType contentType, Object body) {
        JsonNode response;
        try {
            response = client.post()
                    .uri(methodUri(method))
                    .contentType(contentType)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, resp) -> {
                        throw new RestClientResponseException("Non-2xx response", resp.getStatusCode(),
                                resp.getStatusText(), resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new TelegramException("Telegram responded with HTTP " + e.getStatusCode().value()
                    + description(e.getResponseBodyAsByteArray()));
        } catch (RestClientException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new TelegramException("Telegram request failed: " + cause.getClass().getSimpleName());
        }
        if (response == null || !response.path("ok").asBoolean(false)) {
            throw new TelegramException("Telegram responded with HTTP 200 but ok=false");
        }
        return response;
    }

    private JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
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
    private URI methodUri(String method) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/bot{token}/{method}")
                .buildAndExpand(properties.botToken(), method)
                .encode()
                .toUri();
    }
}
