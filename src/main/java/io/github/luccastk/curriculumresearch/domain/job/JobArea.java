package io.github.luccastk.curriculumresearch.domain.job;

import java.util.List;
import java.util.regex.Pattern;

public enum JobArea {
    BACKEND("back[ -]?end|server[ -]?side",
            List.of("desenvolvedor backend", "backend developer", "backend engineer",
                    "desenvolvedor backend junior", "desenvolvedor backend pleno")),
    FRONTEND("front[ -]?end|client[ -]?side|ui (?:developer|engineer|dev)",
            List.of("desenvolvedor frontend", "frontend developer", "frontend engineer",
                    "desenvolvedor frontend junior", "desenvolvedor frontend pleno")),
    FULLSTACK("full[ -]?stack",
            List.of("desenvolvedor fullstack", "fullstack developer", "fullstack engineer",
                    "desenvolvedor fullstack junior", "desenvolvedor fullstack pleno"));

    private final Pattern titleTerm;
    private final List<String> searchQueries;

    JobArea(String titleTerm, List<String> searchQueries) {
        this.titleTerm = TitleMatcher.compile(titleTerm);
        this.searchQueries = searchQueries;
    }

    boolean namedIn(String normalizedTitle) {
        return titleTerm.matcher(normalizedTitle).find();
    }

    public List<String> searchQueries() {
        return searchQueries;
    }
}
