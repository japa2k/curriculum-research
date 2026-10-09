package io.github.luccastk.jobsearch.resume;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

/** Saved applications, one row per job, in the same SQLite file as the seen jobs. */
public class ApplicationStore {

    private final JdbcTemplate jdbc;

    /** Creates the file's parent directory and the {@code application} table when absent. */
    public ApplicationStore(String dbPath) {
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create directory for the applications database: " + parent, e);
        }
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        this.jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS application (job_id TEXT PRIMARY KEY, title TEXT, company TEXT, "
                + "url TEXT NOT NULL, generated_at TEXT NOT NULL)");
    }

    Optional<Application> find(String jobId) {
        return jdbc.query("SELECT job_id, title, company, url, generated_at FROM application WHERE job_id = ?",
                (rs, row) -> new Application(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Instant.parse(rs.getString(5))), jobId).stream().findFirst();
    }

    /** Replaces the job's row when it already has one (the files were regenerated). */
    void save(Application application) {
        jdbc.update("INSERT OR REPLACE INTO application (job_id, title, company, url, generated_at) "
                        + "VALUES (?, ?, ?, ?, ?)", application.jobId(), application.title(), application.company(),
                application.url(), application.generatedAt().toString());
    }
}
