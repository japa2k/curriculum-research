package io.github.luccastk.curriculumresearch.domain.application;

import java.time.Instant;
import java.util.Optional;

/** The writer's usage limit is used up, so no document can be written until it resets. */
public class UsageLimitReachedException extends RuntimeException {
    private final Instant resetsAt;

    /** @param resetsAt when the limit resets, or null when the writer did not say */
    public UsageLimitReachedException(String message, Instant resetsAt) {
        super(message);
        this.resetsAt = resetsAt;
    }

    public Optional<Instant> resetsAt() {
        return Optional.ofNullable(resetsAt);
    }
}
