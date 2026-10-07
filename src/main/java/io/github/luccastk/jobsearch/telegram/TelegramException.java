package io.github.luccastk.jobsearch.telegram;

/**
 * A {@code sendMessage} call that failed. Deliberately carries no cause: Spring's I/O exceptions quote
 * the request URL, which contains the bot token.
 */
public class TelegramException extends RuntimeException {

    public TelegramException(String message) {
        super(message);
    }
}
