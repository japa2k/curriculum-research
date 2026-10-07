package io.github.luccastk.jobsearch;

/** A request parameter outside its allowed values; the message names the parameter. */
public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
