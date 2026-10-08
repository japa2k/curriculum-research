package io.github.luccastk.jobsearch.telegram;

/** One button of an inline keyboard; Telegram sends {@code callbackData} (at most 64 bytes) back when pressed. */
public record InlineButton(String text, String callbackData) {
}
