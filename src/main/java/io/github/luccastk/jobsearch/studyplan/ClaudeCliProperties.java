package io.github.luccastk.jobsearch.studyplan;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How to run the local Claude Code CLI, bound from {@code claude-cli.*} in {@code application.yml}.
 *
 * @param command the executable; on Windows a bare name also finds {@code .exe}/{@code .cmd} on the PATH
 * @param args    fixed arguments; the prompt is never one of them, it goes through stdin
 * @param timeout           how long one run may take before the process is killed
 * @param maxConcurrentRuns how many CLI processes may run at once; further requests are refused, not queued
 */
@ConfigurationProperties("claude-cli")
public record ClaudeCliProperties(String command, List<String> args, Duration timeout, int maxConcurrentRuns) {

    public ClaudeCliProperties {
        args = args == null ? List.of() : List.copyOf(args);
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException("claude-cli.max-concurrent-runs must be at least 1");
        }
    }
}
