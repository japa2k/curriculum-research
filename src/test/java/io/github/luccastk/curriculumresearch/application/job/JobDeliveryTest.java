package io.github.luccastk.curriculumresearch.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.job.JobNotifier;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.infra.persistence.JdbcJobRepository;

class JobDeliveryTest {
    private final List<JobPosting> chat = new ArrayList<>();
    private final Map<JobPosting, Optional<ApplicationKit>> kits = new HashMap<>();
    private final List<String> failingUrls = new ArrayList<>();
    private final JobNotifier notifier = (job, kit) -> {
        if (failingUrls.contains(job.url())) {
            throw new RuntimeException("Telegram refused " + job.url());
        }
        chat.add(job);
        kits.put(job, kit);
    };
    private final ApplicationPreparation preparation = mock(ApplicationPreparation.class);
    private JdbcJobRepository repository;
    private JobDelivery delivery;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new JdbcJobRepository(JdbcClient.create(dataSource));
        delivery = new JobDelivery(repository, preparation, notifier);
        when(preparation.prepare(any())).thenAnswer(call -> kitFor(call.getArgument(0)));
    }

    @Test
    void sendsOneBatchPerDeliveryWithoutRepeatingJobs() {
        List<JobPosting> stored = storeJobs(7);

        assertThat(delivery.deliverNext(5)).isEqualTo(5);
        assertThat(chat).containsExactlyElementsOf(stored.subList(0, 5));

        assertThat(delivery.deliverNext(5)).isEqualTo(2);
        assertThat(chat).containsExactlyElementsOf(stored);

        assertThat(delivery.deliverNext(5)).isZero();
    }

    @Test
    void sendsEachJobWithItsApplicationKit() {
        JobPosting job = storeJobs(1).get(0);

        delivery.deliverNext(5);

        assertThat(kits.get(job)).contains(kitFor(job));
    }

    @Test
    void sendsTheJobWithoutAKitWhenTheKitCannotBeMade() {
        JobPosting job = storeJobs(1).get(0);
        when(preparation.prepare(job)).thenThrow(new IllegalStateException("Claude CLI timed out"));

        assertThat(delivery.deliverNext(5)).isEqualTo(1);

        assertThat(kits.get(job)).isEmpty();
        assertThat(repository.findUnsent(5)).isEmpty();
    }

    @Test
    void retriesAJobThatFailedToSendOnTheNextDelivery() {
        List<JobPosting> stored = storeJobs(2);
        failingUrls.add(stored.get(0).url());

        assertThat(delivery.deliverNext(5)).isEqualTo(1);
        assertThat(chat).containsExactly(stored.get(1));

        failingUrls.clear();
        assertThat(delivery.deliverNext(5)).isEqualTo(1);
        assertThat(chat).containsExactly(stored.get(1), stored.get(0));
    }

    private static ApplicationKit kitFor(JobPosting job) {
        return new ApplicationKit(Path.of(job.url(), "curriculo.pdf"), Path.of(job.url(), "plano-de-estudos.pdf"));
    }

    private List<JobPosting> storeJobs(int count) {
        List<JobPosting> stored = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JobPosting job = new JobPosting("Desenvolvedor Java " + i, "Acme", "SP", "u" + i);
            repository.save(job);
            stored.add(job);
        }
        return stored;
    }
}
