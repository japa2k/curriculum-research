package io.github.luccastk.curriculumresearch.application.job;

import static io.github.luccastk.curriculumresearch.domain.job.JobArea.BACKEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import io.github.luccastk.curriculumresearch.domain.job.JobFilter;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobSource;
import io.github.luccastk.curriculumresearch.infra.persistence.JdbcJobRepository;

class JobSearchTest {
    private final JobSource source = mock(JobSource.class);
    private JdbcJobRepository repository;
    private JobSearch search;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new JdbcJobRepository(JdbcClient.create(dataSource));
        search = new JobSearch(source, new JobFilter(), repository);
    }

    @Test
    void storesOnlyTheJobsThatPassTheFilter() {
        JobPosting backend = new JobPosting("Desenvolvedor Java", "Acme", "SP", "u1");
        JobPosting frontend = new JobPosting("Frontend Developer", "B", "SP", "u2");
        JobPosting qa = new JobPosting("QA Engineer", "C", "SP", "u3");
        when(source.searchJobs("java", "Brasil", 0)).thenReturn(List.of(backend, frontend, qa));

        assertThat(search.search("java", "Brasil", 0, Set.of(BACKEND))).containsExactly(backend);
        assertThat(repository.findAll()).containsExactly(backend);
    }

    @Test
    void returnsOnlyJobsNotFoundBefore() {
        JobPosting seen = new JobPosting("Desenvolvedor Java", "Acme", "SP", "u1");
        JobPosting fresh = new JobPosting("Desenvolvedor Kotlin", "B", "SP", "u2");
        when(source.searchJobs("java", "Brasil", 0)).thenReturn(List.of(seen), List.of(seen, fresh));
        search.search("java", "Brasil", 0, Set.of(BACKEND));

        assertThat(search.search("java", "Brasil", 0, Set.of(BACKEND))).containsExactly(fresh);
    }
}
