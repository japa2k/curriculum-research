package io.github.luccastk.curriculumresearch.infra.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobRepository;

/** Stores found jobs in the SQL {@code jobs} table and their descriptions in {@code job_descriptions}. */
@Repository
public class JdbcJobRepository implements JobRepository {
    private final JdbcClient jdbc;

    public JdbcJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean save(JobPosting job) {
        int inserted = jdbc.sql("""
                INSERT OR IGNORE INTO jobs (url, title, company, location, found_at)
                VALUES (:url, :title, :company, :location, :foundAt)
                """)
                .param("url", job.url())
                .param("title", job.title())
                .param("company", job.company())
                .param("location", job.location())
                .param("foundAt", Instant.now().toString())
                .update();
        return inserted > 0;
    }

    @Override
    public List<JobPosting> findAll() {
        return jdbc.sql("SELECT title, company, location, url FROM jobs ORDER BY found_at")
                .query(JobPosting.class)
                .list();
    }

    @Override
    public List<JobPosting> findUnsent(int limit) {
        return jdbc.sql("""
                SELECT title, company, location, url FROM jobs
                WHERE sent_at IS NULL
                ORDER BY found_at
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(JobPosting.class)
                .list();
    }

    @Override
    public void markSent(JobPosting job) {
        jdbc.sql("UPDATE jobs SET sent_at = :sentAt WHERE url = :url")
                .param("sentAt", Instant.now().toString())
                .param("url", job.url())
                .update();
    }

    @Override
    public Optional<String> findDescription(JobPosting job) {
        return jdbc.sql("SELECT description FROM job_descriptions WHERE url = :url")
                .param("url", job.url())
                .query(String.class)
                .optional();
    }

    @Override
    public void saveDescription(JobPosting job, String description) {
        jdbc.sql("""
                INSERT OR REPLACE INTO job_descriptions (url, description, fetched_at)
                VALUES (:url, :description, :fetchedAt)
                """)
                .param("url", job.url())
                .param("description", description)
                .param("fetchedAt", Instant.now().toString())
                .update();
    }
}
