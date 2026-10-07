package io.github.luccastk.jobsearch.alerts;

import io.github.luccastk.jobsearch.JobPosting;
import java.util.ArrayList;
import java.util.List;

/** Renders a job as a Telegram {@code parse_mode=HTML} message, one field per line. */
final class JobMessage {

    private JobMessage() {
    }

    static String format(JobPosting job) {
        List<String> lines = new ArrayList<>();
        if (job.title() != null) {
            lines.add("<b>" + escape(job.title()) + "</b>");
        }
        if (job.company() != null) {
            lines.add(escape(job.company()));
        }
        if (job.location() != null) {
            lines.add(escape(job.location()));
        }
        if (job.postedAt() != null) {
            lines.add("Posted: " + job.postedAt());
        }
        if (job.url() != null) {
            // Telegram's HTML mode rejects a bare '&' anywhere in the text, so the URL is escaped too.
            lines.add(escape(job.url()));
        }
        return String.join("\n", lines);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
