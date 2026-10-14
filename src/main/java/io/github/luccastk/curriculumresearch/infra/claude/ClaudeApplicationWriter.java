package io.github.luccastk.curriculumresearch.infra.claude;

import static io.github.luccastk.curriculumresearch.infra.claude.ApplicationPrompt.END_MARKER;
import static io.github.luccastk.curriculumresearch.infra.claude.ApplicationPrompt.RESUME_MARKER;
import static io.github.luccastk.curriculumresearch.infra.claude.ApplicationPrompt.STUDY_PLAN_MARKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationDocuments;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationWriter;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

/** Has the local Claude CLI write a job's study plan and tailored résumé from the base résumé. */
@Component
public class ClaudeApplicationWriter implements ApplicationWriter {
    private final ClaudeCli claude;
    private final Path baseResume;
    private final int weeklyHours;
    private final int maxWeeks;

    public ClaudeApplicationWriter(ClaudeCli claude,
            @Value("${resume.base-path}") Path baseResume,
            @Value("${study-plan.weekly-hours}") int weeklyHours,
            @Value("${study-plan.max-weeks}") int maxWeeks) {
        this.claude = claude;
        this.baseResume = baseResume;
        this.weeklyHours = weeklyHours;
        this.maxWeeks = maxWeeks;
    }

    @Override
    public ApplicationDocuments write(JobPosting job, String description) {
        String prompt = ApplicationPrompt.build(job, description, readBaseResume(), weeklyHours, maxWeeks,
                Year.now().getValue());
        return parse(claude.run(prompt));
    }

    /** Read on every job, so an edited base résumé takes effect without a restart. */
    private String readBaseResume() {
        try {
            String resume = Files.readString(baseResume, StandardCharsets.UTF_8);
            if (resume.isBlank()) {
                throw new IllegalStateException("The base résumé at " + baseResume.toAbsolutePath() + " is empty");
            }
            return resume;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the base résumé at " + baseResume.toAbsolutePath()
                    + " (set resume.base-path)", e);
        }
    }

    static ApplicationDocuments parse(String answer) {
        int plan = answer.indexOf(STUDY_PLAN_MARKER);
        int resume = answer.indexOf(RESUME_MARKER);
        int end = answer.indexOf(END_MARKER);
        if (plan < 0 || resume < plan || end < resume) {
            throw new ClaudeCliException("Claude's answer is not in the study plan / résumé format", null);
        }
        String planHtml = answer.substring(plan + STUDY_PLAN_MARKER.length(), resume).strip();
        String resumeHtml = answer.substring(resume + RESUME_MARKER.length(), end).strip();
        if (planHtml.isEmpty() || resumeHtml.isEmpty()) {
            throw new ClaudeCliException("Claude's answer left the study plan or the résumé empty", null);
        }
        return new ApplicationDocuments(planHtml, resumeHtml);
    }
}
