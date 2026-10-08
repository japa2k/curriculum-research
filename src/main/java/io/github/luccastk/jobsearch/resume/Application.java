package io.github.luccastk.jobsearch.resume;

import java.time.Instant;

/** A posting whose résumé, study plan and project were generated and saved; title and company may be null. */
record Application(String jobId, String title, String company, String url, Instant generatedAt) {
}
