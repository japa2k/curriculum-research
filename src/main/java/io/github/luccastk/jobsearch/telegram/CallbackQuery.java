package io.github.luccastk.jobsearch.telegram;

/**
 * A pressed inline button.
 *
 * @param chatId the chat of the message holding the button, or {@code null} when Telegram did not include it
 * @param data   the button's callback data, or {@code null} when absent
 */
public record CallbackQuery(String id, String chatId, String data) {
}
