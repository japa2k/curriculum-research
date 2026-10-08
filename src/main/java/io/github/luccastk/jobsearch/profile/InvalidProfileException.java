package io.github.luccastk.jobsearch.profile;

/** The profile file is missing or invalid; the message names the problem. */
public class InvalidProfileException extends IllegalStateException {

    public InvalidProfileException(String message) {
        super(message);
    }

    public InvalidProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
