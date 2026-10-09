package io.github.luccastk.curriculumresearch.api.telegram.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import io.github.luccastk.curriculumresearch.api.telegram.Bot;

@Configuration
public class TelegramConfig {

    @Bean
    public TelegramBotsApi telegramBotApi(Bot bot) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        bot.sendText("Hello World!");
        return botsApi;
    }
}
