package io.github.luccastk.jobsearch.alerts;

import io.github.luccastk.jobsearch.JobPosting;
import io.github.luccastk.jobsearch.JobSearchService;
import io.github.luccastk.jobsearch.SearchQuery;
import io.github.luccastk.jobsearch.telegram.TelegramClient;
import io.github.luccastk.jobsearch.telegram.TelegramException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One alert cycle: runs every configured search, then sends each job not yet seen to Telegram. While the
 * store is empty the cycle only seeds it, so the first run does not flood the chat with existing jobs.
 */
public class AlertCycle implements Runnable {

    static final Duration SEND_INTERVAL = Duration.ofSeconds(1);

    private static final Logger log = LoggerFactory.getLogger(AlertCycle.class);

    private final List<SearchQuery> searches;
    private final JobSearchService service;
    private final SeenJobStore store;
    private final TelegramClient telegram;
    private final Duration searchDelay;

    /** @param searchDelay minimum wait between consecutive searches */
    public AlertCycle(List<SearchQuery> searches, JobSearchService service, SeenJobStore store,
            TelegramClient telegram, Duration searchDelay) {
        this.searches = List.copyOf(searches);
        this.service = service;
        this.store = store;
        this.telegram = telegram;
        this.searchDelay = searchDelay;
    }

    @Override
    public void run() {
        boolean seeding = store.isEmpty();
        Map<String, JobPosting> found = new LinkedHashMap<>();
        int failed = 0;
        for (int i = 0; i < searches.size(); i++) {
            if (i > 0 && !pause(searchDelay)) {
                return;
            }
            SearchQuery search = searches.get(i);
            try {
                service.search(search).jobs().forEach(job -> found.putIfAbsent(job.id(), job));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Alert search failed: keywords=\"{}\" reason={}", search.keywords(), reason(e));
            }
        }
        List<JobPosting> newJobs = found.values().stream().filter(job -> !store.contains(job.id())).toList();
        int sent = 0;
        if (seeding) {
            newJobs.forEach(job -> store.markSeen(job.id()));
            log.info("Seeded {} jobs on the first run; no messages sent", newJobs.size());
        } else {
            sent = send(newJobs);
        }
        log.info("Alert cycle finished: searchesRun={} searchesFailed={} jobsFound={} newJobs={} messagesSent={}",
                searches.size(), failed, found.size(), newJobs.size(), sent);
    }

    /** Sends in order, marking each job seen as soon as Telegram accepts it; stops at the first failure. */
    private int send(List<JobPosting> jobs) {
        int sent = 0;
        for (JobPosting job : jobs) {
            if (sent > 0 && !pause(SEND_INTERVAL)) {
                break;
            }
            try {
                telegram.sendMessage(JobMessage.format(job));
            } catch (TelegramException e) {
                log.warn("Telegram sendMessage failed for job {}: {}; remaining jobs retry next cycle",
                        job.id(), e.getMessage());
                break;
            }
            store.markSeen(job.id());
            sent++;
        }
        return sent;
    }

    /** Returns {@code false} when interrupted (the application is shutting down). */
    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Alert cycle interrupted; stopping early");
            return false;
        }
    }

    private static String reason(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
