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
 *
 * <p>Mode {@code documents} answers an application prompt with the three marked documents; the résumé is the
 * base résumé found in the prompt plus a project entry. A directive in the posting changes the answer:
 * {@code FAKE_FAIL} exits 3, {@code FAKE_EMPTY} prints only whitespace, {@code FAKE_GARBAGE} prints text without
 * markers, {@code FAKE_SLEEP} sleeps for a minute and {@code FAKE_SLOW} answers after two seconds.
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
            case "documents" -> documents(out, new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
            case "sleep" -> {
                Files.writeString(Path.of(args[1]), String.valueOf(ProcessHandle.current().pid()));
                Thread.sleep(60_000);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static void documents(PrintStream out, String prompt) throws InterruptedException {
        if (prompt.contains("FAKE_FAIL")) {
            System.exit(3);
        } else if (prompt.contains("FAKE_EMPTY")) {
            out.print("  \n ");
        } else if (prompt.contains("FAKE_GARBAGE")) {
            out.print("Desculpe, não sei separar em documentos.");
        } else if (prompt.contains("FAKE_SLEEP")) {
            Thread.sleep(60_000);
        } else {
            if (prompt.contains("FAKE_SLOW")) {
                Thread.sleep(2_000);
            }
            String base = prompt.substring(prompt.indexOf("<curriculo_base>") + "<curriculo_base>".length(),
                    prompt.indexOf("</curriculo_base>")).strip();
            out.print("Aqui estão os documentos.\n===CURRICULO===\n" + base + "\n## Projetos\n- Projeto Fake (2026)\n"
                    + "===PLANO_DE_ESTUDOS===\n# Plano Fake\n- Semana 1: AWS\n"
                    + "===PROJETO===\n# Projeto Fake\n- Stack: AWS\n");
        }
    }

    /** The {@code java} command plus arguments that run this class in the given mode. */
    public static List<String> command(String... modeAndArgs) {
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
