package io.github.luccastk.curriculumresearch.domain.job;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class TitleMatcher {
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    private TitleMatcher() {
    }

    /** Matches the alternatives as whole words, so "java" does not hit "javascript". */
    static Pattern compile(String alternatives) {
        return Pattern.compile("(?<![a-z0-9])(?:" + alternatives + ")(?![a-z0-9])");
    }

    static String normalize(String title) {
        String decomposed = Normalizer.normalize(title, Normalizer.Form.NFD);
        return ACCENTS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
