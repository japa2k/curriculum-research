package io.github.luccastk.curriculumresearch.infra.claude;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opens the local Claude Code CLI for one prompt and closes it when the answer is in. The prompt
 * goes through stdin, never the command line, so posting text never reaches a shell. Tools and MCP
 * servers are off: the prompt carries untrusted posting text, and the answer is all that is needed.
 */
@Component
public class ClaudeCli {
    static final List<String> ARGS = List.of("-p", "--output-format", "text", "--tools", "",
            "--strict-mcp-config", "--no-session-persistence");
    // Java only appends .exe on Windows, so an npm install's claude.cmd shim must be found by hand.
    private static final List<String> WINDOWS_EXTENSIONS = List.of(".exe", ".cmd", ".bat");
    private static final long DRAIN_SECONDS = 5;

    private final String command;
    private final Duration timeout;

    public ClaudeCli(@Value("${claude.command}") String command, @Value("${claude.timeout}") Duration timeout) {
        this.command = command;
        this.timeout = timeout;
    }

    /** Returns the CLI's answer, trimmed; throws when it cannot start, fails, times out or answers nothing. */
    public String run(String prompt) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(resolve(command, File.separatorChar == '\\', System.getenv("PATH")));
        commandLine.addAll(ARGS);

        Process process;
        try {
            process = new ProcessBuilder(commandLine).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new ClaudeCliException("Claude CLI could not be started (" + command + "): " + e.getMessage(), e);
        }

        // Pumped on their own threads, so a large prompt or answer cannot block the timeout.
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture.runAsync(() -> writeAll(process.getOutputStream(), prompt));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process);
                throw new ClaudeCliException("Claude CLI timed out after " + timeout.toSeconds() + " s", null);
            }
            if (process.exitValue() != 0) {
                throw new ClaudeCliException("Claude CLI exited with code " + process.exitValue(), null);
            }
            String output = stdout.get(DRAIN_SECONDS, TimeUnit.SECONDS).trim();
            if (output.isEmpty()) {
                throw new ClaudeCliException("Claude CLI returned empty output", null);
            }
            return output;
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new ClaudeCliException("Interrupted while waiting for the Claude CLI", e);
        } catch (ExecutionException | TimeoutException e) {
            kill(process);
            throw new ClaudeCliException("Claude CLI output could not be read", e);
        }
    }

    /** The file a bare command name runs, searched on the PATH the way a Windows shell does. */
    static String resolve(String command, boolean windows, String path) {
        if (!windows || path == null || command.contains("/") || command.contains("\\") || command.contains(".")) {
            return command;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String extension : WINDOWS_EXTENSIONS) {
                Path candidate = Path.of(dir.strip(), command + extension);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return command;
    }

    /** The CLI may be a shim (claude.cmd) that started the real process as a child. */
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

    private static void writeAll(OutputStream out, String prompt) {
        try (out) {
            out.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // A CLI that exits without reading all of stdin closes the pipe; its exit code tells what happened.
        }
    }
}
