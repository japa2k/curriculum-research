package io.github.luccastk.curriculumresearch.infra.claude;

public class ClaudeCliException extends RuntimeException {

    public ClaudeCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
