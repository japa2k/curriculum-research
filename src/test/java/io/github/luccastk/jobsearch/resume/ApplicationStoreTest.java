package io.github.luccastk.jobsearch.resume;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luccastk.jobsearch.alerts.SeenJobStore;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationStoreTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-10-08T12:00:00Z");

    @TempDir
    Path dir;

    @Test
    void recordsAnApplicationAndFindsItByJobId() {
        ApplicationStore store = new ApplicationStore(dir.resolve("db/jobs.db").toString());
        Application application = new Application("4242", "Dev", "Acme", "https://x/4242", GENERATED_AT);

        store.save(application);

        assertThat(store.find("4242")).contains(application);
        assertThat(store.find("9999")).isEmpty();
    }

    @Test
    void keepsTitleAndCompanyNullWhenUnknown() {
        ApplicationStore store = new ApplicationStore(dir.resolve("jobs.db").toString());
        Application application = new Application("4242", null, null, "https://x/4242", GENERATED_AT);

        store.save(application);

        assertThat(store.find("4242")).contains(application);
    }

    @Test
    void replacesTheRecordWhenAJobIsGeneratedAgain() {
        ApplicationStore store = new ApplicationStore(dir.resolve("jobs.db").toString());
        store.save(new Application("4242", "Old", "Acme", "https://x/4242", GENERATED_AT));

        Application regenerated = new Application("4242", "New", "Acme", "https://x/4242", GENERATED_AT.plusSeconds(60));
        store.save(regenerated);

        assertThat(store.find("4242")).contains(regenerated);
    }

    @Test
    void sharesTheDatabaseFileWithTheSeenJobs() {
        String dbPath = dir.resolve("jobs.db").toString();
        SeenJobStore seen = new SeenJobStore(dbPath);
        seen.markSeen("1");

        ApplicationStore store = new ApplicationStore(dbPath);
        store.save(new Application("4242", "Dev", "Acme", "https://x/4242", GENERATED_AT));

        assertThat(seen.contains("1")).isTrue();
        assertThat(new ApplicationStore(dbPath).find("4242")).isPresent();
    }
}
