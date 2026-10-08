package io.github.luccastk.jobsearch.linkedin;

/**
 * What a guest job detail page says about one posting; any field is {@code null} when absent or empty.
 *
 * @param seniorityLevel the "Seniority level" criterion as LinkedIn words it (e.g. "Entry level")
 */
public record JobDetail(String title, String company, String description, String seniorityLevel) {
}
