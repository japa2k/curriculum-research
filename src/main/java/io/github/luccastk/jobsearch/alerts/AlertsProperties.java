package io.github.luccastk.jobsearch.alerts;

import io.github.luccastk.jobsearch.PostedWithin;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Alert settings, bound from {@code alerts.*} in {@code application.yml}.
 *
 * @param enabled  when {@code false}, no cycle is scheduled and the Telegram settings are not required
 * @param interval wait between the end of one cycle and the start of the next
 * @param dbPath   SQLite file holding the seen job ids
 */
@ConfigurationProperties("alerts")
public record AlertsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1h") Duration interval,
        @DefaultValue("./data/jobs.db") String dbPath,
        List<Search> searches) {

    /** One LinkedIn search, with the same names and defaults as {@code GET /api/jobs/search}. */
    public record Search(
            String keywords,
            String location,
            @DefaultValue("ANY") PostedWithin postedWithin,
            @DefaultValue("false") String remote,
            @DefaultValue("25") int maxResults) {
    }
}
