package io.github.luccastk.curriculumresearch.domain.application;

import java.time.Instant;
import java.util.Optional;

/** Where it is announced that the writer hit its usage limit, and that it is back. */
public interface UsageLimitNotifier {

    /** @param resetsAt when the limit resets, empty when it is not known */
    void limitReached(Optional<Instant> resetsAt);

    void limitLifted();
}
