package io.github.luccastk.jobsearch.resume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** A job's generated files, under {@code <applicationsDir>/<jobId>/}. */
class ApplicationFiles {

    static final String RESUME = "curriculo.docx";
    static final String STUDY_PLAN = "plano-de-estudos.md";
    static final String PROJECT = "projeto.md";

    /** The saved files' contents. */
    record Saved(byte[] resume, byte[] studyPlan, byte[] project) {
    }

    private final Path applicationsDir;

    ApplicationFiles(Path applicationsDir) {
        this.applicationsDir = applicationsDir;
    }

    /** Creates the job's directory when absent and replaces any file already there. */
    void write(String jobId, byte[] resume, String studyPlan, String project) throws IOException {
        Path dir = Files.createDirectories(applicationsDir.resolve(jobId));
        Files.write(dir.resolve(RESUME), resume);
        Files.writeString(dir.resolve(STUDY_PLAN), studyPlan, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(PROJECT), project, StandardCharsets.UTF_8);
    }

    /** The three files, or empty when any of them is missing or unreadable. */
    Optional<Saved> read(String jobId) {
        Path dir = applicationsDir.resolve(jobId);
        try {
            return Optional.of(new Saved(Files.readAllBytes(dir.resolve(RESUME)),
                    Files.readAllBytes(dir.resolve(STUDY_PLAN)), Files.readAllBytes(dir.resolve(PROJECT))));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** {@code curriculo-<company-slug>-<jobId>.docx}, or {@code curriculo-<jobId>.docx} when the slug is empty. */
    static String resumeFileName(String company, String jobId) {
        String slug = company == null ? "" : Normalizer.normalize(company, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "curriculo-" + jobId + ".docx" : "curriculo-" + slug + "-" + jobId + ".docx";
    }
}
