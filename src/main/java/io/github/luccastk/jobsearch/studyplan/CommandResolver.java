package io.github.luccastk.jobsearch.studyplan;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds the file a bare command name runs on Windows. Java only appends {@code .exe} there, so an npm
 * install's {@code claude.cmd} shim would not start as plain {@code claude}; this searches the PATH the way
 * a shell does. Elsewhere, or when the name already has an extension or a path, it is used as given.
 */
final class CommandResolver {

    private static final List<String> WINDOWS_EXTENSIONS = List.of(".exe", ".cmd", ".bat");

    private CommandResolver() {
    }

    static String resolve(String command) {
        return resolve(command, File.separatorChar == '\\', System.getenv("PATH"));
    }

    static String resolve(String command, boolean windows, String path) {
        if (!windows || path == null || command.contains("/") || command.contains("\\") || command.contains(".")) {
            return command;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String extension : WINDOWS_EXTENSIONS) {
                Path candidate = Path.of(dir.trim(), command + extension);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return command;
    }
}
