package io.github.luccastk.jobsearch.telegram;

import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Long-polls {@code getUpdates} on its own thread, independently of the alert cycle, and hands each callback
 * query from the configured chat to a handler. Callbacks from any other chat and every other kind of update
 * are skipped; each update is confirmed (the offset moves past it) whether it was handled or not.
 */
public class CallbackPoller implements SmartLifecycle {

    static final Duration LONG_POLL = Duration.ofSeconds(25);
    static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(CallbackPoller.class);

    private final TelegramClient client;
    private final String chatId;
    private final Consumer<CallbackQuery> handler;
    private final Duration longPoll;

    private volatile boolean running;
    private Thread thread;

    /** @param handler runs on the polling thread, so it must return quickly */
    public CallbackPoller(TelegramClient client, String chatId, Consumer<CallbackQuery> handler) {
        this(client, chatId, handler, LONG_POLL);
    }

    /** Tests pass a shorter {@code longPoll}. */
    CallbackPoller(TelegramClient client, String chatId, Consumer<CallbackQuery> handler, Duration longPoll) {
        this.client = client;
        this.chatId = chatId;
        this.handler = handler;
        this.longPoll = longPoll;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = Thread.ofPlatform().name("telegram-poller").daemon().start(this::poll);
    }

    /** Interrupts a poll in flight and waits briefly for the thread to end. */
    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        thread.interrupt();
        try {
            thread.join(RETRY_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Telegram polling stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void poll() {
        long offset = 0;
        while (running) {
            try {
                for (TelegramUpdate update : client.getUpdates(offset, longPoll)) {
                    offset = update.updateId() + 1;
                    dispatch(update.callbackQuery());
                }
            } catch (TelegramException e) {
                if (!running) {
                    return;
                }
                log.warn("Telegram getUpdates failed: {}; retrying in {} s", e.getMessage(), RETRY_DELAY.toSeconds());
                if (!pause()) {
                    return;
                }
            } catch (RuntimeException e) {
                // Anything else would end the thread while isRunning() still reports true.
                log.warn("Telegram polling failed: {}; retrying in {} s", e.getClass().getSimpleName(),
                        RETRY_DELAY.toSeconds());
                if (!pause()) {
                    return;
                }
            }
        }
    }

    private void dispatch(CallbackQuery query) {
        if (query == null) {
            return;
        }
        if (!chatId.equals(query.chatId())) {
            log.warn("Ignored a button press from a chat other than TELEGRAM_CHAT_ID");
            return;
        }
        try {
            handler.accept(query);
        } catch (RuntimeException e) {
            log.warn("Button press handler failed: {}", e.getClass().getSimpleName());
        }
    }

    /** Returns {@code false} when interrupted (the application is shutting down). */
    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
