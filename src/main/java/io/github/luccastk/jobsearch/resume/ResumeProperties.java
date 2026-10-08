package io.github.luccastk.jobsearch.resume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the button's résumé feature reads and writes, bound from {@code resume.*}; both paths hold contact data,
 * so they belong under the gitignored {@code data/} directory.
 *
 * @param basePath        the engineer's base résumé, in Markdown; required while alerts are enabled
 * @param applicationsDir each job's generated files go to {@code <applicationsDir>/<jobId>/}
 */
@ConfigurationProperties("resume")
public record ResumeProperties(
        @DefaultValue("./data/resume-base.md") String basePath,
        @DefaultValue("./data/applications") String applicationsDir) {

    /** @throws IllegalStateException naming {@code resume.base-path} when the file is missing, unreadable or blank */
    String readBaseResume() {
        try {
            String markdown = Files.readString(Path.of(basePath));
            if (!markdown.isBlank()) {
                return markdown.strip();
            }
        } catch (IOException | InvalidPathException e) {
            // Reported below.
        }
        throw new IllegalStateException("resume.base-path (" + basePath + ") must be a non-blank Markdown résumé: "
                + "create it as the README describes (or set alerts.enabled=false)");
    }
}
