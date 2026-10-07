package io.github.luccastk.jobsearch;

import java.time.LocalDate;

public record JobPosting(String id, String title, String company, String location, String url, LocalDate postedAt) {
}
