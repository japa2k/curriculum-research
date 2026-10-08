package io.github.luccastk.jobsearch.studyplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Runs the real process plumbing against {@link FakeClaude}. */
@ExtendWith(OutputCaptureExtension.class)
class ClaudeCliTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Test
    void sendsThePromptThroughStdinAndReturnsTheTrimmedOutput() {
        String prompt = "Plano para a vaga: Ação & C++ \"quoted\" $HOME %PATH%";

        String output = cli(FakeClaude.command("echo", "--fixed", "arg"), TIMEOUT).run(prompt);

        assertThat(output).isEqualTo("ARGS: --fixed arg\nSTDIN:\n" + prompt);
    }

    @Test
    void sendsLargePromptsWithoutBlocking() {
        String prompt = "x".repeat(1_000_000);

        String output = cli(FakeClaude.command("echo"), TIMEOUT).run(prompt);

        assertThat(output).endsWith(prompt);
    }

    @Test
    void failsWhenTheCommandCannotBeStarted(CapturedOutput log) {
        ClaudeCli cli = cli(List.of("no-such-claude-command-4242"), TIMEOUT);

        assertThatThrownBy(() -> cli.run("prompt"))
                .isInstanceOf(StudyPlanGenerationException.class)
                .hasMessageContaining("could not be started");
        assertThat(warnLines(log)).singleElement().asString().contains("could not be started");
    }

    @Test
    void failsWhenTheCommandExitsNonZero(CapturedOutput log) {
        ClaudeCli cli = cli(FakeClaude.command("fail"), TIMEOUT);

        assertThatThrownBy(() -> cli.run("prompt"))
                .isInstanceOf(StudyPlanGenerationException.class)
                .hasMessageContaining("exited with code 3");
        assertThat(warnLines(log)).singleElement().asString().contains("exited with code 3");
    }

    @Test
    void failsWhenTheOutputIsEmpty(CapturedOutput log) {
        ClaudeCli cli = cli(FakeClaude.command("empty"), TIMEOUT);

        assertThatThrownBy(() -> cli.run("prompt"))
                .isInstanceOf(StudyPlanGenerationException.class)
                .hasMessageContaining("empty output");
        assertThat(warnLines(log)).singleElement().asString().contains("empty output");
    }

    @Test
    void killsTheProcessWhenItExceedsTheTimeout(@TempDir Path dir, CapturedOutput log) throws Exception {
        Path pidFile = dir.resolve("pid");
        ClaudeCli cli = cli(FakeClaude.command("sleep", pidFile.toString()), Duration.ofSeconds(3));

        long started = System.nanoTime();
        assertThatThrownBy(() -> cli.run("prompt"))
                .isInstanceOf(StudyPlanGenerationException.class)
                .hasMessageContaining("timed out after 3 s");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(15));
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        ProcessHandle.of(pid).ifPresent(process -> assertThat(process.onExit())
                .succeedsWithin(Duration.ofSeconds(5)));
        assertThat(warnLines(log)).singleElement().asString().contains("timed out");
    }

    private static ClaudeCli cli(List<String> command, Duration timeout) {
        return new ClaudeCli(new ClaudeCliProperties(command.getFirst(), command.subList(1, command.size()), timeout));
    }

    private static List<String> warnLines(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("Claude CLI")).toList();
    }
}
