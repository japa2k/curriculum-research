package io.github.luccastk.jobsearch.telegram;

/** One {@code getUpdates} entry; {@code callbackQuery} is {@code null} for every other kind of update. */
public record TelegramUpdate(long updateId, CallbackQuery callbackQuery) {
}
