package io.github.luccastk.jobsearch.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@ExtendWith(OutputCaptureExtension.class)
class AlertSchedulerTest {

    private static final long INTERVAL_MS = 200;
    private static final long CYCLE_MS = 300;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

    @AfterEach
    void tearDown() {
        registrar.destroy();
        taskScheduler.shutdown();
    }

    @Test
    void runsTheFirstCycleImmediatelyAndEachNextOneIntervalAfterThePreviousFinished() {
        List<long[]> runs = new CopyOnWriteArrayList<>();
        long scheduledAt = start(() -> {
            long begin = System.nanoTime();
            sleep(CYCLE_MS);
            runs.add(new long[] {begin, System.nanoTime()});
        });

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.size() >= 3);

        assertThat(millis(runs.get(0)[0] - scheduledAt)).isLessThan(INTERVAL_MS);
        for (int i = 1; i < 3; i++) {
            assertThat(millis(runs.get(i)[0] - runs.get(i - 1)[1])).isGreaterThanOrEqualTo(INTERVAL_MS);
        }
    }

    @Test
    void logsAFailedCycleAtErrorAndStillRunsTheNextOne(CapturedOutput output) {
        AtomicInteger runs = new AtomicInteger();
        start(() -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("database is locked");
            }
        });

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 2);

        assertThat(output.getOut().lines().filter(line -> line.contains("ERROR")).toList())
                .anyMatch(line -> line.contains("Alert cycle failed"));
        assertThat(output.getOut()).contains("database is locked");
    }

    /** Returns when scheduling began, taken after the thread pool is up so its start-up cost is not counted. */
    private long start(Runnable cycle) {
        taskScheduler.initialize();
        registrar.setTaskScheduler(taskScheduler);
        new AlertScheduler(cycle, Duration.ofMillis(INTERVAL_MS)).configureTasks(registrar);
        long scheduledAt = System.nanoTime();
        registrar.afterPropertiesSet();
        return scheduledAt;
    }

    private static long millis(long nanos) {
        return Duration.ofNanos(nanos).toMillis();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
