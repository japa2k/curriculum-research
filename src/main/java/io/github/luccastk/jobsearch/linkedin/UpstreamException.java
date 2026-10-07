package io.github.luccastk.jobsearch.linkedin;

/** A LinkedIn page request that failed; the message is the reason reported to API clients. */
public class UpstreamException extends RuntimeException {

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
