package io.github.luccastk.jobsearch.studyplan;

import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stand-in for the Claude CLI, run as a real child process: {@code java -cp <test-classes> FakeClaude <mode> ...}.
 * Modes: {@code echo} prints its arguments and stdin; {@code fail} exits 3; {@code empty} prints only
 * whitespace; {@code sleep <pidFile>} writes its pid to the file and sleeps for a minute.
 */
public final class FakeClaude {

    private FakeClaude() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        switch (args[0]) {
            case "echo" -> {
                String stdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                out.print("\n  ARGS: " + String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        + "\nSTDIN:\n" + stdin + "\n\n");
            }
            case "fail" -> {
                System.in.readAllBytes();
                System.err.println("boom");
                System.exit(3);
            }
            case "empty" -> {
                System.in.readAllBytes();
                out.print("  \n ");
            }
            case "sleep" -> {
                Files.writeString(Path.of(args[1]), String.valueOf(ProcessHandle.current().pid()));
                Thread.sleep(60_000);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** The {@code java} command plus arguments that run this class in the given mode. */
    static List<String> command(String... modeAndArgs) {
        String javaBinary = ProcessHandle.current().info().command().orElseThrow();
        String classes;
        try {
            classes = Path.of(FakeClaude.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<String> command = new ArrayList<>(List.of(javaBinary, "-cp", classes, FakeClaude.class.getName()));
        command.addAll(Arrays.asList(modeAndArgs));
        return command;
    }
}
