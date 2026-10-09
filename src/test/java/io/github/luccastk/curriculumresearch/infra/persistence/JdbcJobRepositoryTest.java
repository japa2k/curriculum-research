package io.github.luccastk.curriculumresearch.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

class JdbcJobRepositoryTest {
    private JdbcJobRepository repository;

    @BeforeEach
    void setUp() {
        // An in-memory SQLite database lives only as long as its connection, so keep one open.
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new JdbcJobRepository(JdbcClient.create(dataSource));
    }

    @Test
    void savesAFoundJob() {
        JobPosting job = new JobPosting("Desenvolvedor Java", "Acme", "São Paulo", "https://linkedin.com/jobs/view/1");

        assertThat(repository.save(job)).isTrue();
        assertThat(repository.findAll()).containsExactly(job);
    }

    @Test
    void storesTheSameUrlOnlyOnce() {
        JobPosting job = new JobPosting("Desenvolvedor Java", "Acme", "São Paulo", "https://linkedin.com/jobs/view/1");
        repository.save(job);

        assertThat(repository.save(job)).isFalse();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void findsUnsentJobsOldestFirstUpToTheLimit() {
        JobPosting first = new JobPosting("Desenvolvedor Java", "A", "SP", "u1");
        JobPosting second = new JobPosting("Desenvolvedor Go", "B", "SP", "u2");
        JobPosting third = new JobPosting("Desenvolvedor React", "C", "SP", "u3");
        repository.save(first);
        repository.save(second);
        repository.save(third);

        assertThat(repository.findUnsent(2)).containsExactly(first, second);
    }

    @Test
    void sentJobsAreNoLongerUnsent() {
        JobPosting sent = new JobPosting("Desenvolvedor Java", "A", "SP", "u1");
        JobPosting pending = new JobPosting("Desenvolvedor Go", "B", "SP", "u2");
        repository.save(sent);
        repository.save(pending);

        repository.markSent(sent);

        assertThat(repository.findUnsent(5)).containsExactly(pending);
        assertThat(repository.findAll()).containsExactly(sent, pending);
    }

    @Test
    void storesAJobsDescription() {
        JobPosting job = new JobPosting("Desenvolvedor Java", "A", "SP", "u1");
        repository.save(job);
        assertThat(repository.findDescription(job)).isEmpty();

        repository.saveDescription(job, "Vaga Java\n- Spring Boot");

        assertThat(repository.findDescription(job)).contains("Vaga Java\n- Spring Boot");
    }
}
