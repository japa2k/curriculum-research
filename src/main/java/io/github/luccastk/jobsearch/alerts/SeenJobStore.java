package io.github.luccastk.jobsearch.alerts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

/** Ids of jobs already alerted (or seeded), in a SQLite file; rows are never deleted. */
public class SeenJobStore {

    private final JdbcTemplate jdbc;

    /** Creates the file's parent directory and the {@code seen_job} table when absent. */
    public SeenJobStore(String dbPath) {
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create directory for the seen-jobs database: " + parent, e);
        }
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        this.jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS seen_job (job_id TEXT PRIMARY KEY, first_seen_at TEXT NOT NULL)");
    }

    public boolean isEmpty() {
        return !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM seen_job)", Boolean.class));
    }

    public boolean contains(String jobId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM seen_job WHERE job_id = ?)", Boolean.class, jobId));
    }

    /** Keeps the original {@code first_seen_at} when the job is already recorded. */
    public void markSeen(String jobId) {
        jdbc.update("INSERT OR IGNORE INTO seen_job (job_id, first_seen_at) VALUES (?, ?)",
                jobId, Instant.now().toString());
    }
}
