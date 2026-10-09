package io.github.luccastk.curriculumresearch.application.job;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationDocuments;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationWriter;
import io.github.luccastk.curriculumresearch.domain.application.DocumentRenderer;
import io.github.luccastk.curriculumresearch.domain.application.DocumentRenderer.Style;
import io.github.luccastk.curriculumresearch.domain.job.JobDescriptionSource;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobRepository;

/**
 * Makes the application kit for a job: reads its description (once, then from the database), has
 * the study plan and the tailored résumé written for it and saves both as PDFs in
 * {@code <dir>/<job id>/}. A kit already on disk is reused, so a job whose message failed to send
 * does not cost another generation.
 */
@Service
public class ApplicationPreparation {
    static final String RESUME_FILE = "curriculo.pdf";
    static final String STUDY_PLAN_FILE = "plano-de-estudos.pdf";

    private final JobRepository jobs;
    private final JobDescriptionSource descriptions;
    private final ApplicationWriter writer;
    private final DocumentRenderer renderer;
    private final Path dir;

    public ApplicationPreparation(JobRepository jobs, JobDescriptionSource descriptions, ApplicationWriter writer,
            DocumentRenderer renderer, @Value("${applications.dir}") Path dir) {
        this.jobs = jobs;
        this.descriptions = descriptions;
        this.writer = writer;
        this.renderer = renderer;
        this.dir = dir;
    }

    /** Throws when the description cannot be read, the documents cannot be written or saved. */
    public ApplicationKit prepare(JobPosting job) {
        Path jobDir = dir.resolve(job.id());
        ApplicationKit kit = new ApplicationKit(jobDir.resolve(RESUME_FILE), jobDir.resolve(STUDY_PLAN_FILE));
        if (Files.isRegularFile(kit.resume()) && Files.isRegularFile(kit.studyPlan())) {
            return kit;
        }

        ApplicationDocuments documents = writer.write(job, description(job));
        byte[] resume = renderer.toPdf(documents.resumeHtml(), Style.RESUME);
        byte[] studyPlan = renderer.toPdf(documents.studyPlanHtml(), Style.STUDY_PLAN);
        try {
            Files.createDirectories(jobDir);
            Files.write(kit.studyPlan(), studyPlan);
            Files.write(kit.resume(), resume);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save the application kit in " + jobDir, e);
        }
        return kit;
    }

    private String description(JobPosting job) {
        return jobs.findDescription(job).orElseGet(() -> {
            String description = descriptions.fetchDescription(job);
            jobs.saveDescription(job, description);
            return description;
        });
    }
}
