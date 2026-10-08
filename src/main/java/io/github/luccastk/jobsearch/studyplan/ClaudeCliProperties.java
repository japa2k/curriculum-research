package io.github.luccastk.jobsearch.studyplan;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How to run the local Claude Code CLI, bound from {@code claude-cli.*} in {@code application.yml}.
 *
 * @param command the executable; on Windows a bare name also finds {@code .exe}/{@code .cmd} on the PATH
 * @param args    fixed arguments; the prompt is never one of them, it goes through stdin
 * @param timeout how long one run may take before the process is killed
 */
@ConfigurationProperties("claude-cli")
public record ClaudeCliProperties(String command, List<String> args, Duration timeout) {

    public ClaudeCliProperties {
        args = args == null ? List.of() : List.copyOf(args);
    }
}
