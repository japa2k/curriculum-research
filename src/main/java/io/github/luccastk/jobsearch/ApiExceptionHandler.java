package io.github.luccastk.jobsearch;

import io.github.luccastk.jobsearch.linkedin.UpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps failures to {@code {"error": "..."}} bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidParameter(InvalidParameterException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError typeMismatch(MethodArgumentTypeMismatchException e) {
        return new ApiError("invalid value '" + e.getValue() + "' for " + e.getName());
    }

    @ExceptionHandler(UpstreamException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError upstream(UpstreamException e) {
        return new ApiError(e.getMessage());
    }
}
