package io.github.luccastk.jobsearch.studyplan;

/** LinkedIn answered 404 for a posting's detail page. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
