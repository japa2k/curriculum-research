package io.github.luccastk.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeenJobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTheParentDirectoryAndTheSeenJobTable() throws SQLException {
        Path dbFile = tempDir.resolve("nested/dir/jobs.db");

        new SeenJobStore(dbFile.toString());

        assertThat(dbFile).exists();
        assertThat(query(dbFile, "SELECT sql FROM sqlite_master WHERE type = 'table'"))
                .singleElement().asString()
                .contains("seen_job")
                .contains("job_id TEXT PRIMARY KEY")
                .contains("first_seen_at TEXT NOT NULL");
    }

    @Test
    void isEmptyUntilAJobIsMarkedSeen() {
        SeenJobStore store = new SeenJobStore(tempDir.resolve("jobs.db").toString());
        assertThat(store.isEmpty()).isTrue();

        store.markSeen("42");

        assertThat(store.isEmpty()).isFalse();
    }

    @Test
    void containsOnlyJobsMarkedSeen() {
        SeenJobStore store = new SeenJobStore(tempDir.resolve("jobs.db").toString());

        store.markSeen("42");

        assertThat(store.contains("42")).isTrue();
        assertThat(store.contains("43")).isFalse();
    }

    @Test
    void recordsFirstSeenAtAsAnIsoUtcInstantAndKeepsItOnRepeatedMarks() throws SQLException {
        Path dbFile = tempDir.resolve("jobs.db");
        SeenJobStore store = new SeenJobStore(dbFile.toString());
        Instant before = Instant.now();

        store.markSeen("42");
        List<String> first = query(dbFile, "SELECT first_seen_at FROM seen_job WHERE job_id = '42'");
        store.markSeen("42");

        List<String> after = query(dbFile, "SELECT first_seen_at FROM seen_job WHERE job_id = '42'");
        assertThat(after).isEqualTo(first);
        assertThat(after.get(0)).endsWith("Z");
        assertThat(Instant.parse(after.get(0))).isBetween(before.minusSeconds(1), Instant.now());
    }

    @Test
    void keepsSeenJobsAcrossReopening() {
        String dbPath = tempDir.resolve("jobs.db").toString();
        new SeenJobStore(dbPath).markSeen("42");

        SeenJobStore reopened = new SeenJobStore(dbPath);

        assertThat(reopened.contains("42")).isTrue();
    }

    private static List<String> query(Path dbFile, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }
}
