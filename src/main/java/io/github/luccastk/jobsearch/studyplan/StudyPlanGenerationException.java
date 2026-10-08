package io.github.luccastk.jobsearch.studyplan;

/** The Claude CLI did not produce a study plan; the message says what happened. */
public class StudyPlanGenerationException extends RuntimeException {

    public StudyPlanGenerationException(String message) {
        super(message);
    }

    public StudyPlanGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
