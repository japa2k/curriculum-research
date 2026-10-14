package io.github.luccastk.curriculumresearch.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import io.github.luccastk.curriculumresearch.domain.application.UsageLimitNotifier;
import io.github.luccastk.curriculumresearch.domain.application.UsageLimitReachedException;
import io.github.luccastk.curriculumresearch.domain.job.ApplyMethod;
import io.github.luccastk.curriculumresearch.domain.job.ApplyMethodSource;
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
    private final List<String> limitNotices = new ArrayList<>();
    private final UsageLimitNotifier limitNotifier = new UsageLimitNotifier() {
        @Override
        public void limitReached(Optional<Instant> resetsAt) {
            limitNotices.add("reached " + resetsAt.map(Instant::toString).orElse("?"));
        }

        @Override
        public void limitLifted() {
            limitNotices.add("lifted");
        }
    };
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T12:00:00Z"));
    private final ApplicationPreparation preparation = mock(ApplicationPreparation.class);
    private final ApplyMethodSource applyMethodSource = mock(ApplyMethodSource.class);
    private JdbcJobRepository repository;
    private JobDelivery delivery;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        repository = new JdbcJobRepository(JdbcClient.create(dataSource));
        delivery = new JobDelivery(repository, preparation, applyMethodSource, notifier, limitNotifier, clock);
        when(preparation.prepare(any())).thenAnswer(call -> kitFor(call.getArgument(0)));
        when(applyMethodSource.applyMethod(any())).thenReturn(ApplyMethod.EASY_APPLY);
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
    void skipsForGoodTheJobsWithoutEasyApplyWithoutMakingTheirKit() {
        List<JobPosting> stored = storeJobs(3);
        when(applyMethodSource.applyMethod(stored.get(0))).thenReturn(ApplyMethod.EXTERNAL);
        when(applyMethodSource.applyMethod(stored.get(1))).thenReturn(ApplyMethod.UNKNOWN);

        assertThat(delivery.deliverNext(5)).isEqualTo(1);

        assertThat(chat).containsExactly(stored.get(2));
        assertThat(repository.findUnsent(5)).isEmpty();
        verify(preparation, never()).prepare(stored.get(0));
        verify(preparation, never()).prepare(stored.get(1));
    }

    @Test
    void fillsTheBatchWithTheJobsAfterTheSkippedOnes() {
        List<JobPosting> stored = storeJobs(4);
        when(applyMethodSource.applyMethod(stored.get(0))).thenReturn(ApplyMethod.EXTERNAL);

        assertThat(delivery.deliverNext(2)).isEqualTo(2);

        assertThat(chat).containsExactly(stored.get(1), stored.get(2));
    }

    @Test
    void keepsTheJobForTheNextDeliveryWhenItsApplyMethodCannotBeRead() {
        JobPosting job = storeJobs(1).get(0);
        when(applyMethodSource.applyMethod(job)).thenThrow(new IllegalStateException("HTTP 429"));

        assertThat(delivery.deliverNext(5)).isZero();
        assertThat(repository.findUnsent(5)).containsExactly(job);

        doReturn(ApplyMethod.EASY_APPLY).when(applyMethodSource).applyMethod(job);
        assertThat(delivery.deliverNext(5)).isEqualTo(1);
        assertThat(chat).containsExactly(job);
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

    @Test
    void holdsTheJobsAndWarnsOnceWhenTheUsageLimitIsReached() {
        List<JobPosting> stored = storeJobs(3);
        Instant reset = clock.instant().plus(Duration.ofHours(2));
        doThrow(new UsageLimitReachedException("limit", reset)).when(preparation).prepare(any());

        assertThat(delivery.deliverNext(5)).isZero();
        assertThat(delivery.deliverNext(5)).isZero();

        assertThat(chat).isEmpty();
        assertThat(repository.findUnsent(5)).containsExactlyElementsOf(stored);
        assertThat(limitNotices).containsExactly("reached " + reset);
    }

    @Test
    void waitsForTheResetTimeAndThenSaysClaudeIsBack() {
        List<JobPosting> stored = storeJobs(2);
        Instant reset = clock.instant().plus(Duration.ofHours(2));
        doThrow(new UsageLimitReachedException("limit", reset)).when(preparation).prepare(any());
        delivery.deliverNext(5);

        doAnswer(call -> kitFor(call.getArgument(0))).when(preparation).prepare(any());
        clock.advance(Duration.ofHours(1));
        assertThat(delivery.deliverNext(5)).isZero();

        clock.advance(Duration.ofHours(1));
        assertThat(delivery.deliverNext(5)).isEqualTo(2);
        assertThat(chat).containsExactlyElementsOf(stored);
        assertThat(limitNotices).containsExactly("reached " + reset, "lifted");
    }

    @Test
    void keepsTryingWhenTheResetTimeIsUnknown() {
        JobPosting job = storeJobs(1).get(0);
        doThrow(new UsageLimitReachedException("limit", null)).when(preparation).prepare(any());
        delivery.deliverNext(5);

        doAnswer(call -> kitFor(call.getArgument(0))).when(preparation).prepare(any());
        assertThat(delivery.deliverNext(5)).isEqualTo(1);

        assertThat(kits.get(job)).contains(kitFor(job));
        assertThat(limitNotices).containsExactly("reached ?", "lifted");
    }

    @Test
    void saysClaudeIsBackAtTheResetTimeEvenWithNoJobsWaiting() {
        storeJobs(1);
        Instant reset = clock.instant().plus(Duration.ofHours(1));
        doThrow(new UsageLimitReachedException("limit", reset)).when(preparation).prepare(any());
        delivery.deliverNext(5);
        repository.markSent(repository.findUnsent(1).get(0));

        clock.advance(Duration.ofHours(1));
        delivery.deliverNext(5);

        assertThat(limitNotices).containsExactly("reached " + reset, "lifted");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
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
