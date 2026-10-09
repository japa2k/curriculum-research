package io.github.luccastk.jobsearch.studyplan;

/** Every Claude CLI run slot was taken, so the run was refused rather than queued. */
public class ClaudeCliBusyException extends StudyPlanGenerationException {

    public ClaudeCliBusyException(String message) {
        super(message);
    }
}
