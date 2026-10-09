package io.github.luccastk.curriculumresearch.domain.job;

import java.util.Optional;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;

/** Where found jobs are announced. Throws when the job could not be sent. */
public interface JobNotifier {

    /** Sends the job with its application kit, or with a notice that the kit could not be made. */
    void send(JobPosting job, Optional<ApplicationKit> kit);
}
