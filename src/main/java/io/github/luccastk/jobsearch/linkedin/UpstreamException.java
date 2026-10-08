package io.github.luccastk.jobsearch.linkedin;

/**
 * A LinkedIn request that failed; the message is the reason reported to API clients.
 * {@link #status()} is the HTTP status LinkedIn answered, or {@code 0} when there was no response.
 */
public class UpstreamException extends RuntimeException {

    private final int status;

    public UpstreamException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public UpstreamException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
