package io.github.luccastk.curriculumresearch.domain.job;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record JobPosting(String title, String company, String location, String url) {
    // LinkedIn view URLs end in the posting id: .../jobs/view/desenvolvedor-java-at-acme-4012345678
    private static final Pattern TRAILING_ID = Pattern.compile("(\\d+)/?$");

    /** The posting id at the end of the URL, or a hash of the URL when it has none. */
    public String id() {
        Matcher matcher = TRAILING_ID.matcher(url);
        return matcher.find() ? matcher.group(1) : Integer.toHexString(url.hashCode());
    }
}
