package io.github.luccastk.jobsearch;

/** The request thread was interrupted while waiting between page requests. */
public class SearchInterruptedException extends RuntimeException {

    public SearchInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
