package io.github.luccastk.jobsearch.telegram;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bot API settings, bound from {@code telegram.*}; the token and chat id come from {@code .env} or the
 * environment ({@code TELEGRAM_BOT_TOKEN}, {@code TELEGRAM_CHAT_ID}).
 *
 * @param timeout connect and read timeout of each request
 */
@ConfigurationProperties("telegram")
public record TelegramProperties(
        @DefaultValue("https://api.telegram.org") String baseUrl,
        String botToken,
        String chatId,
        @DefaultValue("10s") Duration timeout) {

    /** Masks the bot token, so logging these properties never leaks it. */
    @Override
    public String toString() {
        return "TelegramProperties[baseUrl=" + baseUrl + ", botToken=" + (isBlank(botToken) ? "<blank>" : "****")
                + ", chatId=" + chatId + ", timeout=" + timeout + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
