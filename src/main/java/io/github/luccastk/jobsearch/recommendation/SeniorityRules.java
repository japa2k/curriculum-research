package io.github.luccastk.jobsearch.recommendation;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Seniority read from a posting's title, falling back to LinkedIn's "Seniority level" criterion. */
public final class SeniorityRules {

    private static final Pattern SENIOR_OR_ABOVE = words(
            "senior", "sênior", "sr", "lead", "líder", "staff", "principal", "specialist", "especialista",
            "manager", "gerente", "head", "director", "diretor");
    private static final Pattern JUNIOR = words(
            "junior", "júnior", "jr", "estágio", "estagiário", "intern", "trainee");
    private static final Pattern PLENO = words("pleno", "pl", "mid", "mid-level");

    private SeniorityRules() {
    }

    public static boolean isSeniorOrAbove(String title) {
        return SENIOR_OR_ABOVE.matcher(title).find();
    }

    /** @param seniorityLevelCriterion LinkedIn's "Seniority level" value, or {@code null} when absent */
    public static Seniority classify(String title, String seniorityLevelCriterion) {
        if (JUNIOR.matcher(title).find()) {
            return Seniority.JUNIOR;
        }
        if (PLENO.matcher(title).find()) {
            return Seniority.PLENO;
        }
        if (seniorityLevelCriterion == null) {
            return Seniority.UNKNOWN;
        }
        return switch (seniorityLevelCriterion.trim().toLowerCase()) {
            case "internship", "entry level" -> Seniority.JUNIOR;
            case "associate" -> Seniority.PLENO;
            default -> Seniority.UNKNOWN;
        };
    }

    /** Case-insensitive whole words: not touching a letter or digit on either side. */
    private static Pattern words(String... words) {
        String alternatives = String.join("|", Arrays.stream(words).map(Pattern::quote).toList());
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
