package io.github.luccastk.curriculumresearch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.TelegramBotsApi;

import io.github.luccastk.curriculumresearch.api.telegram.Bot;

@SpringBootTest(properties = { "telegram.bot.token=test-token", "spring.datasource.url=jdbc:sqlite::memory:" })
class CurriculumResearchApplicationTests {

	@MockitoBean
	TelegramBotsApi telegramBotsApi;

	@Autowired
	Bot bot;

	@Test
	void shouldReturnBotName() {
		String name = bot.getBotUsername();
		assert (name.equals("aetherJPjobs_bot"));
	}

}
