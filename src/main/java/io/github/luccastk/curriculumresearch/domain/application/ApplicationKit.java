package io.github.luccastk.curriculumresearch.domain.application;

import java.nio.file.Path;

/** The PDFs made for one job: a résumé tailored to it and a plan to study for it. */
public record ApplicationKit(Path resume, Path studyPlan) {
}
