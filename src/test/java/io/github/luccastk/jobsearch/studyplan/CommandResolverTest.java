package io.github.luccastk.jobsearch.studyplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandResolverTest {

    @TempDir
    Path first;

    @TempDir
    Path second;

    @Test
    void findsANpmShimOnWindows() throws IOException {
        Path shim = Files.createFile(second.resolve("claude.cmd"));

        assertThat(CommandResolver.resolve("claude", true, path(first, second))).isEqualTo(shim.toString());
    }

    @Test
    void prefersAnExecutableAndTheEarlierPathEntry() throws IOException {
        Files.createFile(first.resolve("claude.cmd"));
        Path exe = Files.createFile(first.resolve("claude.exe"));
        Files.createFile(second.resolve("claude.exe"));

        assertThat(CommandResolver.resolve("claude", true, path(first, second))).isEqualTo(exe.toString());
    }

    @Test
    void leavesTheCommandAloneWhenNothingMatches() {
        assertThat(CommandResolver.resolve("claude", true, path(first, second))).isEqualTo("claude");
        assertThat(CommandResolver.resolve("claude", true, null)).isEqualTo("claude");
    }

    @Test
    void leavesTheCommandAloneOutsideWindows() throws IOException {
        Files.createFile(first.resolve("claude.cmd"));

        assertThat(CommandResolver.resolve("claude", false, path(first))).isEqualTo("claude");
    }

    @Test
    void leavesExplicitExtensionsAndPathsAlone() throws IOException {
        Files.createFile(first.resolve("claude.cmd"));
        String explicit = first.resolve("claude").toString();

        assertThat(CommandResolver.resolve("claude.cmd", true, path(first))).isEqualTo("claude.cmd");
        assertThat(CommandResolver.resolve(explicit, true, path(first))).isEqualTo(explicit);
    }

    private static String path(Path... dirs) {
        StringBuilder path = new StringBuilder();
        for (Path dir : dirs) {
            path.append(dir).append(File.pathSeparator);
        }
        return path.toString();
    }
}
