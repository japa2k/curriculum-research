package io.github.luccastk.curriculumresearch.domain.job;

/** How a posting is applied to. */
public enum ApplyMethod {
    /** LinkedIn's Easy Apply ("Candidatura simplificada"): applied to without leaving LinkedIn. */
    EASY_APPLY,
    /** On the company's own site, reached from the posting's Apply button. */
    EXTERNAL,
    /** Neither, as on a closed or removed posting. */
    UNKNOWN
}
