package io.github.luccastk.curriculumresearch.api.telegram;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import io.github.luccastk.curriculumresearch.api.telegram.commands.Reply;
import io.github.luccastk.curriculumresearch.domain.application.ApplicationKit;
import io.github.luccastk.curriculumresearch.domain.job.JobNotifier;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.infra.scheduling.JobSearchFinished;

@Component
public class Bot
        extends TelegramLongPollingBot implements JobNotifier {
    private final String token;
    private final String username;
    private final String chatId;
    private final CommandHandlers commandHandlers;

    public Bot(@Value("${telegram.bot.token}") String token,
            @Value("${telegram.bot.username}") String username,
            @Value("${telegram.chat.id}") String chatId,
            CommandHandlers commandHandlers) {
        this.token = token;
        this.username = username;
        this.chatId = chatId;
        this.commandHandlers = commandHandlers;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        String messageText = update.getMessage().getText();

        if (!messageText.startsWith("/")) {
            sendText("Please use a command starting with /");
            return;
        }

        String[] parts = messageText.substring(1).split("\\s+", 2);
        String commandName = parts[0].split("@")[0];
        String args = parts.length > 1 ? parts[1].trim() : "";

        for (Reply reply : commandHandlers.HandleCommand(commandName, args)) {
            if (reply.hasApplyButton()) {
                sendJob(reply.text(), reply.applyUrl());
            } else {
                sendText(reply.text());
            }
        }
    }

    /** The jobs themselves go out with the delivery schedule; here only a search asked for in the chat is answered. */
    @EventListener
    public void onJobSearchFinished(JobSearchFinished finished) {
        if (!finished.requested()) {
            return;
        }
        int found = finished.newJobs().size();
        sendText(switch (found) {
            case 0 -> "Nenhuma vaga nova encontrada.";
            case 1 -> "1 vaga nova na fila, ela chega aqui no próximo envio.";
            default -> found + " vagas novas na fila, elas chegam aqui aos poucos.";
        });
    }

    @Override
    public void send(JobPosting job, Optional<ApplicationKit> kit) {
        String text = job.title() + "\n" + job.company() + " - " + job.location();
        if (kit.isEmpty()) {
            sendJob(text + "\n\nNão consegui gerar o currículo e o plano de estudo desta vaga.", job.url());
            return;
        }
        sendJob(text, job.url());
        String slug = slug(job.company());
        sendDocument(kit.get().resume(), "curriculo-" + slug + ".pdf", "Currículo para " + job.title());
        sendDocument(kit.get().studyPlan(), "plano-de-estudos-" + slug + ".pdf", "Plano de estudo para " + job.title());
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    public void sendText(String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(what).build();
        send(sm);
    }

    public void sendJob(String what, String applyUrl) {
        InlineKeyboardButton applyButton = InlineKeyboardButton.builder()
                .text("Candidatar")
                .url(applyUrl)
                .build();
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(applyButton))
                .build();
        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(what)
                .replyMarkup(keyboard).build();
        send(sm);
    }

    private void sendDocument(Path file, String fileName, String caption) {
        SendDocument document = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(file.toFile(), fileName))
                .caption(caption)
                .build();
        try {
            execute(document);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    static String slug(String text) {
        String slug = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "vaga" : slug;
    }

    private void send(SendMessage sm) {
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
