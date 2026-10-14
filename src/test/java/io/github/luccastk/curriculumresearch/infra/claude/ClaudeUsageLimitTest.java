package io.github.luccastk.curriculumresearch.infra.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import io.github.luccastk.curriculumresearch.domain.application.UsageLimitReachedException;

class ClaudeUsageLimitTest {
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-09T14:20:00-03:00[America/Sao_Paulo]");

    @Test
    void readsTheResetTimeFromTheEpochSuffix() {
        assertThat(ClaudeUsageLimit.in("Claude AI usage limit reached|1760040000", NOW))
                .flatMap(UsageLimitReachedException::resetsAt)
                .contains(Instant.ofEpochSecond(1760040000));
    }

    @Test
    void readsTheResetHourInTheZoneTheMessageGives() {
        assertThat(ClaudeUsageLimit.in("5-hour limit reached ∙ resets 3pm (America/Sao_Paulo)", NOW))
                .flatMap(UsageLimitReachedException::resetsAt)
                .contains(Instant.parse("2026-10-09T18:00:00Z"));
    }

    @Test
    void aResetHourAlreadyPastIsTomorrow() {
        assertThat(ClaudeUsageLimit.in("You've hit your session limit · resets 2:30am", NOW))
                .flatMap(UsageLimitReachedException::resetsAt)
                .contains(Instant.parse("2026-10-10T05:30:00Z"));
    }

    @Test
    void aLimitWithoutAReadableResetTimeHasNone() {
        var limit = ClaudeUsageLimit.in("Weekly limit reached ∙ resets Oct 12", NOW);

        assertThat(limit).isPresent();
        assertThat(limit.get().resetsAt()).isEmpty();
    }

    @Test
    void otherErrorsAreNotALimit() {
        assertThat(ClaudeUsageLimit.in("Invalid API key · Please run /login", NOW)).isEmpty();
    }
}
