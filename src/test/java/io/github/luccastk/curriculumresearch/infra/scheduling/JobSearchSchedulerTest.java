package io.github.luccastk.curriculumresearch.infra.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.github.luccastk.curriculumresearch.application.job.JobSearch;
import io.github.luccastk.curriculumresearch.domain.job.JobArea;
import io.github.luccastk.curriculumresearch.domain.job.JobFilter;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;

class JobSearchSchedulerTest {
    private final JobSearch search = mock(JobSearch.class);
    private final JobFilter filter = new JobFilter();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<Runnable> background = new ArrayList<>();
    private final JobSearchScheduler scheduler = new JobSearchScheduler(
            search, filter, "Brasil", Duration.ZERO, events, background::add);

    @Test
    void runsEveryQueryForEveryArea() {
        scheduler.searchAll();

        for (String query : filter.searchQueries()) {
            verify(search).search(query, "Brasil", 0, EnumSet.allOf(JobArea.class));
        }
    }

    @Test
    void keepsSearchingWhenAQueryFails() {
        String first = filter.searchQueries().get(0);
        when(search.search(eq(first), anyString(), anyInt(), any()))
                .thenThrow(new UncheckedIOException(new IOException("HTTP 429")));

        scheduler.searchAll();

        verify(search, times(filter.searchQueries().size())).search(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void publishesTheNewJobsOfAScheduledRun() {
        JobPosting job = new JobPosting("Desenvolvedor Java", "Acme", "SP", "u1");
        String first = filter.searchQueries().get(0);
        when(search.search(eq(first), anyString(), anyInt(), any())).thenReturn(List.of(job));

        scheduler.searchAll();

        verify(events).publishEvent(new JobSearchFinished(List.of(job), false));
    }

    @Test
    void searchNowRunsInTheBackgroundAndMarksTheRunAsRequested() {
        assertThat(scheduler.searchNow()).isTrue();
        verify(events, never()).publishEvent(any(Object.class));

        background.forEach(Runnable::run);

        verify(events).publishEvent(new JobSearchFinished(List.of(), true));
    }

    @Test
    void doesNotStartASecondSearchWhileOneIsRunning() {
        assertThat(scheduler.searchNow()).isTrue();

        assertThat(scheduler.searchNow()).isFalse();
        scheduler.searchAll();
        verify(search, never()).search(anyString(), anyString(), anyInt(), any());

        background.forEach(Runnable::run);
        assertThat(scheduler.searchNow()).isTrue();
    }
}
