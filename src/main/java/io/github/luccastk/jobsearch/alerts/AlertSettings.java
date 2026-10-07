package io.github.luccastk.jobsearch.alerts;

import io.github.luccastk.jobsearch.InvalidParameterException;
import io.github.luccastk.jobsearch.SearchQuery;
import io.github.luccastk.jobsearch.telegram.TelegramProperties;
import java.util.ArrayList;
import java.util.List;

/** Alert settings checked at startup, so a misconfiguration fails fast instead of on the first cycle. */
record AlertSettings(List<SearchQuery> searches, String dbPath) {

    /** @throws IllegalStateException naming the missing or invalid setting (never its value) */
    static AlertSettings from(AlertsProperties alerts, TelegramProperties telegram) {
        requireSet(telegram.botToken(), "TELEGRAM_BOT_TOKEN");
        requireSet(telegram.chatId(), "TELEGRAM_CHAT_ID");
        if (alerts.searches() == null || alerts.searches().isEmpty()) {
            throw new IllegalStateException("alerts.searches must contain at least one search "
                    + "(or set alerts.enabled=false)");
        }
        List<SearchQuery> searches = new ArrayList<>();
        for (int i = 0; i < alerts.searches().size(); i++) {
            AlertsProperties.Search search = alerts.searches().get(i);
            try {
                searches.add(SearchQuery.of(search.keywords(), search.location(), search.postedWithin(),
                        search.remote(), search.maxResults()));
            } catch (InvalidParameterException e) {
                // The message starts with the parameter name, e.g. "maxResults must be between 1 and 100".
                throw new IllegalStateException("alerts.searches[" + i + "]." + e.getMessage());
            }
        }
        return new AlertSettings(List.copyOf(searches), alerts.dbPath());
    }

    private static void requireSet(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is not set: add it to .env or the environment "
                    + "(or set alerts.enabled=false)");
        }
    }
}
