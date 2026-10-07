package io.github.luccastk.jobsearch.alerts;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Runs the alert cycle once at startup, then {@code interval} after each cycle finishes, so cycles never
 * overlap. A failed cycle is logged and the schedule carries on.
 */
public class AlertScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final Runnable cycle;
    private final Duration interval;

    public AlertScheduler(Runnable cycle, Duration interval) {
        this.cycle = cycle;
        this.interval = interval;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::runCycle, interval);
    }

    private void runCycle() {
        try {
            cycle.run();
        } catch (RuntimeException e) {
            log.error("Alert cycle failed; the next cycle runs in {}", interval, e);
        }
    }
}
