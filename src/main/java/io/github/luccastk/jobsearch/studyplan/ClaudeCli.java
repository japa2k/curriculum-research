package io.github.luccastk.jobsearch.studyplan;

import io.github.luccastk.jobsearch.SearchInterruptedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the local Claude Code CLI once per prompt. The prompt goes through stdin, never the command line,
 * so posting text is never interpreted by a shell; stdin and stdout are pumped on their own threads so a
 * large prompt or answer cannot block the timeout.
 */
@Component
public class ClaudeCli {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCli.class);

    /** How long to wait for stdout to drain after the process has exited. */
    private static final long DRAIN_SECONDS = 5;

    private final ClaudeCliProperties properties;

    public ClaudeCli(ClaudeCliProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the CLI's stdout, trimmed
     * @throws StudyPlanGenerationException when the CLI cannot start, exits non-zero, times out or prints nothing
     */
    public String run(String prompt) {
        List<String> command = new ArrayList<>();
        command.add(CommandResolver.resolve(properties.command()));
        command.addAll(properties.args());

        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw failure("Claude CLI could not be started (" + properties.command() + "): " + e.getMessage(), e);
        }

        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture.runAsync(() -> writeAll(process.getOutputStream(), prompt));
        try {
            if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                throw failure("Claude CLI timed out after " + properties.timeout().toSeconds() + " s", null);
            }
            if (process.exitValue() != 0) {
                throw failure("Claude CLI exited with code " + process.exitValue(), null);
            }
            String output = stdout.get(DRAIN_SECONDS, TimeUnit.SECONDS).trim();
            if (output.isEmpty()) {
                throw failure("Claude CLI returned empty output", null);
            }
            return output;
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new SearchInterruptedException("interrupted while waiting for the Claude CLI", e);
        } catch (ExecutionException | TimeoutException e) {
            kill(process);
            throw failure("Claude CLI output could not be read: " + e.getMessage(), e);
        }
    }

    private StudyPlanGenerationException failure(String message, Throwable cause) {
        log.warn(message);
        return new StudyPlanGenerationException(message, cause);
    }

    /** The CLI may be a shim (e.g. {@code claude.cmd}) that started the real process as a child. */
    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A CLI that exits without reading all of stdin closes the pipe; its exit code tells what happened. */
    private static void writeAll(OutputStream out, String prompt) {
        try (out) {
            out.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Ignored: see above.
        }
    }
}
