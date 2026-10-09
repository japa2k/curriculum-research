package io.github.luccastk.curriculumresearch.domain.application;

/** Turns the body of an HTML page into a PDF. */
public interface DocumentRenderer {

    enum Style {
        RESUME, STUDY_PLAN
    }

    byte[] toPdf(String bodyHtml, Style style);
}
