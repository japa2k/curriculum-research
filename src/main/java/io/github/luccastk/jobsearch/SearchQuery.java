package io.github.luccastk.jobsearch;

/** A validated job search; {@code location} is {@code null} when not given. */
public record SearchQuery(String keywords, String location, PostedWithin postedWithin, boolean remote, int maxResults) {
}
