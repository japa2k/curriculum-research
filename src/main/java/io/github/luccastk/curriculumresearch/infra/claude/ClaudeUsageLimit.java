package io.github.luccastk.curriculumresearch.infra.claude;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luccastk.curriculumresearch.domain.application.UsageLimitReachedException;

/**
 * Recognizes the Claude CLI's usage limit messages, such as "Claude AI usage limit reached|1760040000"
 * or "5-hour limit reached ∙ resets 3pm (America/Sao_Paulo)", and reads when the limit resets.
 */
final class ClaudeUsageLimit {
    private static final Pattern LIMIT = Pattern.compile("(?i)limit (reached|will reset)|hit your [\\w -]{0,20}limit");
    private static final Pattern EPOCH = Pattern.compile("\\|(\\d{10})\\b");
    private static final Pattern RESET_TIME = Pattern.compile(
            "(?i)resets?\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?:\\s*\\(([^)]+)\\))?");

    private ClaudeUsageLimit() {
    }

    /** The limit the CLI reported in {@code text}, or empty when the text is not a limit message. */
    static Optional<UsageLimitReachedException> in(String text, ZonedDateTime now) {
        if (!LIMIT.matcher(text).find()) {
            return Optional.empty();
        }
        return Optional.of(new UsageLimitReachedException("Claude usage limit reached: " + text.strip(),
                resetsAt(text, now)));
    }

    /** Null when the text gives no reset time this can read. */
    private static Instant resetsAt(String text, ZonedDateTime now) {
        Matcher epoch = EPOCH.matcher(text);
        if (epoch.find()) {
            return Instant.ofEpochSecond(Long.parseLong(epoch.group(1)));
        }
        Matcher time = RESET_TIME.matcher(text);
        if (!time.find()) {
            return null;
        }
        int hour = Integer.parseInt(time.group(1));
        int minute = time.group(2) == null ? 0 : Integer.parseInt(time.group(2));
        if (time.group(3) != null) {
            hour = hour % 12 + (time.group(3).equalsIgnoreCase("pm") ? 12 : 0);
        }
        if (hour > 23 || minute > 59) {
            return null;
        }
        ZonedDateTime local = now.withZoneSameInstant(zone(time.group(4), now.getZone()));
        ZonedDateTime reset = local.with(LocalTime.of(hour, minute));
        return (reset.isAfter(local) ? reset : reset.plusDays(1)).toInstant();
    }

    private static ZoneId zone(String name, ZoneId fallback) {
        try {
            return name == null ? fallback : ZoneId.of(name.strip());
        } catch (DateTimeException e) {
            return fallback;
        }
    }
}
