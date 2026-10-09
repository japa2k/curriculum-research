package io.github.luccastk.jobsearch.telegram;

/** Escaping for {@code parse_mode=HTML} messages, which Telegram rejects when they hold a bare '&', '<' or '>'. */
public final class TelegramHtml {

    private TelegramHtml() {
    }

    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
