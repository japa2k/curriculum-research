package io.github.luccastk.jobsearch.resume;

import io.github.luccastk.jobsearch.studyplan.StudyPlanGenerationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The three Markdown documents one CLI run produces, each trimmed and non-blank. */
record ApplicationDocuments(String resume, String studyPlan, String project) {

    static final String RESUME_MARKER = "===CURRICULO===";
    static final String STUDY_PLAN_MARKER = "===PLANO_DE_ESTUDOS===";
    static final String PROJECT_MARKER = "===PROJETO===";

    private static final Pattern LAYOUT = Pattern.compile(
            "(?s)" + line(RESUME_MARKER) + "(.*?)" + line(STUDY_PLAN_MARKER) + "(.*?)" + line(PROJECT_MARKER) + "(.*)");

    /**
     * Text before the first marker is dropped (the CLI sometimes opens with a sentence).
     *
     * @throws StudyPlanGenerationException when a marker is missing, repeated or out of order, or a document is blank
     */
    static ApplicationDocuments parse(String output) {
        Matcher matcher = LAYOUT.matcher(output);
        if (!matcher.find()) {
            throw unsplittable();
        }
        String resume = matcher.group(1).strip();
        String studyPlan = matcher.group(2).strip();
        String project = matcher.group(3).strip();
        boolean repeated = Pattern.compile(Pattern.quote(RESUME_MARKER) + "|" + Pattern.quote(STUDY_PLAN_MARKER) + "|"
                + Pattern.quote(PROJECT_MARKER)).matcher(resume + studyPlan + project).find();
        if (repeated || resume.isEmpty() || studyPlan.isEmpty() || project.isEmpty()) {
            throw unsplittable();
        }
        return new ApplicationDocuments(resume, studyPlan, project);
    }

    private static String line(String marker) {
        return "(?m:^)" + Pattern.quote(marker) + "[ \\t]*(?:\\R|$)";
    }

    private static StudyPlanGenerationException unsplittable() {
        return new StudyPlanGenerationException("Claude CLI output could not be split into the three documents");
    }
}
