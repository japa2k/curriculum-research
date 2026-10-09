package io.github.luccastk.curriculumresearch.domain.application;

/** The study plan and the tailored résumé for one job, each as the body of an HTML page. */
public record ApplicationDocuments(String studyPlanHtml, String resumeHtml) {
}
