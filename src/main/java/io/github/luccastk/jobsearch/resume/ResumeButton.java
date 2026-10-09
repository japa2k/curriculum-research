package io.github.luccastk.jobsearch.resume;

import io.github.luccastk.jobsearch.telegram.InlineButton;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The "generate résumé" button on each alert; its callback data is {@code resume:<jobId>}, at most 27 bytes. */
public final class ResumeButton {

    static final String LABEL = "📄 Gerar currículo";

    private static final String PREFIX = "resume:";
    private static final Pattern PAYLOAD = Pattern.compile(Pattern.quote(PREFIX) + "([0-9]{1,20})");

    private ResumeButton() {
    }

    public static InlineButton forJob(String jobId) {
        return new InlineButton(LABEL, PREFIX + jobId);
    }

    /** The job id a pressed button carries, or empty when {@code callbackData} is not one of these buttons. */
    public static Optional<String> jobId(String callbackData) {
        if (callbackData == null) {
            return Optional.empty();
        }
        Matcher matcher = PAYLOAD.matcher(callbackData);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
