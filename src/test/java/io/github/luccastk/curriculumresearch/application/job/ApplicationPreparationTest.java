package io.github.luccastk.curriculumresearch.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationDocuments;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationWriter;
import io.github.luccastk.curriculumresearch.domain.application.DocumentRenderer;
import io.github.luccastk.curriculumresearch.domain.job.JobDescriptionSource;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.infra.persistence.JdbcJobRepository;

class ApplicationPreparationTest {
    private static final JobPosting JOB = new JobPosting("Desenvolvedor Java", "Acme", "SP",
            "https://br.linkedin.com/jobs/view/desenvolvedor-java-at-acme-4012345678");

    @TempDir
    Path dir;
    private final List<JobPosting> fetched = new ArrayList<>();
    private final List<String> written = new ArrayList<>();
    private final JobDescriptionSource descriptions = job -> {
        fetched.add(job);
        return "Vaga Java com Spring Boot";
    };
    private final ApplicationWriter writer = (job, description) -> {
        written.add(description);
        return new ApplicationDocuments("<h1>plano</h1>", "<h1>curriculo</h1>");
    };
    // The "PDF" is the HTML and the style, so the test can tell which document landed in which file.
    private final DocumentRenderer renderer = (html, style) -> (style + " " + html).getBytes(StandardCharsets.UTF_8);
    private JdbcJobRepository repository;
    private ApplicationPreparation preparation;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new JdbcJobRepository(JdbcClient.create(dataSource));
        repository.save(JOB);
        preparation = new ApplicationPreparation(repository, descriptions, writer, renderer, dir);
    }

    @Test
    void savesTheResumeAndTheStudyPlanAsPdfsInTheJobsFolder() throws Exception {
        ApplicationKit kit = preparation.prepare(JOB);

        assertThat(kit.resume()).isEqualTo(dir.resolve("4012345678").resolve("curriculo.pdf"));
        assertThat(Files.readString(kit.resume())).isEqualTo("RESUME <h1>curriculo</h1>");
        assertThat(Files.readString(kit.studyPlan())).isEqualTo("STUDY_PLAN <h1>plano</h1>");
        assertThat(written).containsExactly("Vaga Java com Spring Boot");
    }

    @Test
    void storesTheDescriptionAndReadsItFromLinkedInOnlyOnce() throws Exception {
        preparation.prepare(JOB);
        Files.delete(dir.resolve("4012345678").resolve("curriculo.pdf"));

        preparation.prepare(JOB);

        assertThat(fetched).containsExactly(JOB);
        assertThat(repository.findDescription(JOB)).contains("Vaga Java com Spring Boot");
        assertThat(written).hasSize(2);
    }

    @Test
    void reusesAKitAlreadyOnDiskWithoutWritingItAgain() {
        ApplicationKit first = preparation.prepare(JOB);

        assertThat(preparation.prepare(JOB)).isEqualTo(first);
        assertThat(written).hasSize(1);
    }

    @Test
    void savesNothingWhenTheDocumentsCannotBeWritten() {
        ApplicationWriter failing = (job, description) -> {
            throw new IllegalStateException("Claude CLI timed out");
        };
        preparation = new ApplicationPreparation(repository, descriptions, failing, renderer, dir);

        assertThatThrownBy(() -> preparation.prepare(JOB)).hasMessageContaining("timed out");
        assertThat(dir.resolve("4012345678")).doesNotExist();
    }
}
