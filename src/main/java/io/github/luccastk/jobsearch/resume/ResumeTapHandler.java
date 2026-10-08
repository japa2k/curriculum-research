package io.github.luccastk.jobsearch.resume;

import static io.github.luccastk.jobsearch.telegram.TelegramHtml.escape;

import io.github.luccastk.jobsearch.SearchInterruptedException;
import io.github.luccastk.jobsearch.linkedin.JobDetail;
import io.github.luccastk.jobsearch.linkedin.UpstreamException;
import io.github.luccastk.jobsearch.profile.Profile;
import io.github.luccastk.jobsearch.studyplan.ClaudeCli;
import io.github.luccastk.jobsearch.studyplan.ClaudeCliBusyException;
import io.github.luccastk.jobsearch.studyplan.JobNotFoundException;
import io.github.luccastk.jobsearch.studyplan.StudyPlanGenerationException;
import io.github.luccastk.jobsearch.studyplan.StudyPlanService;
import io.github.luccastk.jobsearch.studyplan.StudyPlanService.ScoredPosting;
import io.github.luccastk.jobsearch.telegram.CallbackQuery;
import io.github.luccastk.jobsearch.telegram.TelegramClient;
import io.github.luccastk.jobsearch.telegram.TelegramException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Handles a press of an alert's "generate résumé" button from the configured chat. It answers the press at once,
 * then, off the polling thread, sends the job's saved files or generates them: reads and scores the posting, asks
 * the Claude CLI for a tailored résumé, a study plan and a project brief, saves the three files, records the
 * application and sends the files. Every outcome is reported in the chat. Logs name job ids, never document text.
 */
public class ResumeTapHandler implements Consumer<CallbackQuery>, AutoCloseable {

    static final String INVALID_BUTTON = "Botão inválido.";
    static final String ALREADY_GENERATING = "Já estou gerando esse currículo, aguarde.";
    static final String POSTING_GONE = "Essa vaga não está mais disponível no LinkedIn.";
    static final String POSTING_UNREADABLE = "Não consegui ler a vaga no LinkedIn agora. Tente de novo mais tarde.";
    static final String SAVE_FAILED = "Não consegui salvar os arquivos.";

    private static final Logger log = LoggerFactory.getLogger(ResumeTapHandler.class);

    private final TelegramClient telegram;
    private final StudyPlanService postings;
    private final ClaudeCli cli;
    private final Profile profile;
    private final String baseResume;
    private final ApplicationFiles files;
    private final ApplicationStore store;
    private final Clock clock;
    private final Set<String> generating = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    ResumeTapHandler(TelegramClient telegram, StudyPlanService postings, ClaudeCli cli, Profile profile,
            String baseResume, ApplicationFiles files, ApplicationStore store, Clock clock) {
        this.telegram = telegram;
        this.postings = postings;
        this.cli = cli;
        this.profile = profile;
        this.baseResume = baseResume;
        this.files = files;
        this.store = store;
        this.clock = clock;
    }

    /** Returns quickly: the work for a valid press runs on its own thread. */
    @Override
    public void accept(CallbackQuery query) {
        Optional<String> jobId = ResumeButton.jobId(query.data());
        if (jobId.isEmpty()) {
            answer(query.id(), INVALID_BUTTON);
            return;
        }
        answer(query.id(), null);
        String id = jobId.get();
        if (!generating.add(id)) {
            reply(ALREADY_GENERATING, id);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    deliver(id);
                } finally {
                    generating.remove(id);
                }
            });
        } catch (RejectedExecutionException e) {
            // The application is shutting down.
            generating.remove(id);
        }
    }

    /** Whether no job is being generated or sent right now. */
    boolean idle() {
        return generating.isEmpty();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void deliver(String jobId) {
        try {
            Optional<Application> saved = store.find(jobId);
            Optional<ApplicationFiles.Saved> savedFiles = saved.flatMap(application -> files.read(jobId));
            if (saved.isPresent() && savedFiles.isPresent()) {
                reply(progress(saved.get().title(), saved.get().company(), jobId), jobId);
                send(saved.get(), savedFiles.get());
            } else {
                generate(jobId);
            }
        } catch (SearchInterruptedException e) {
            // The application is shutting down.
        } catch (RuntimeException e) {
            log.error("Delivering the application for job {} failed", jobId, e);
        }
    }

    private void generate(String jobId) {
        ScoredPosting posting;
        try {
            posting = postings.read(jobId);
        } catch (JobNotFoundException e) {
            reply(POSTING_GONE, jobId);
            return;
        } catch (UpstreamException e) {
            reply(POSTING_UNREADABLE, jobId);
            return;
        }
        JobDetail detail = posting.detail();
        reply(progress(detail.title(), detail.company(), jobId), jobId);

        ApplicationDocuments documents;
        try {
            documents = ApplicationDocuments.parse(cli.run(ApplicationPrompt.build(detail, profile.knownSkills(),
                    posting.score().skills().matchedSkills(), posting.score().skills().missingSkills(),
                    profile.studyPlan(), baseResume, Year.now(clock).getValue())));
        } catch (ClaudeCliBusyException e) {
            reply(generationFailed("ocupado"), jobId);
            return;
        } catch (StudyPlanGenerationException e) {
            log.warn("Application generation failed for job {}: {}", jobId, e.getMessage());
            reply(generationFailed("falha na geração"), jobId);
            return;
        }

        byte[] resume;
        Application application = new Application(jobId, detail.title(), detail.company(),
                StudyPlanService.JOB_URL + jobId, clock.instant());
        try {
            resume = ResumeDocx.render(documents.resume());
            files.write(jobId, resume, documents.studyPlan(), documents.project());
            store.save(application);
        } catch (IOException | UncheckedIOException | DataAccessException e) {
            log.warn("Could not save the application for job {}: {}", jobId, e.toString());
            reply(SAVE_FAILED, jobId);
            return;
        }
        send(application, new ApplicationFiles.Saved(resume, documents.studyPlan().getBytes(StandardCharsets.UTF_8),
                documents.project().getBytes(StandardCharsets.UTF_8)));
    }

    /** Résumé, study plan, project; stops at the first failure, and the saved files are sent again next press. */
    private void send(Application application, ApplicationFiles.Saved saved) {
        try {
            telegram.sendDocument(ApplicationFiles.resumeFileName(application.company(), application.jobId()),
                    saved.resume(), caption(application));
            telegram.sendDocument(ApplicationFiles.STUDY_PLAN, saved.studyPlan(), null);
            telegram.sendDocument(ApplicationFiles.PROJECT, saved.project(), null);
        } catch (TelegramException e) {
            log.warn("Telegram sendDocument failed for job {}: {}; the saved files are sent again on the next press",
                    application.jobId(), e.getMessage());
        }
    }

    private void answer(String callbackQueryId, String text) {
        try {
            telegram.answerCallbackQuery(callbackQueryId, text);
        } catch (TelegramException e) {
            log.warn("Telegram answerCallbackQuery failed: {}", e.getMessage());
        }
    }

    private void reply(String htmlText, String jobId) {
        try {
            telegram.sendMessage(htmlText);
        } catch (TelegramException e) {
            log.warn("Telegram sendMessage failed for job {}: {}", jobId, e.getMessage());
        }
    }

    /** Title and company each fall back to the job id, which then appears once. */
    private static String progress(String title, String company, String jobId) {
        String shownTitle = title == null ? jobId : title;
        String shownCompany = company == null ? jobId : company;
        String posting = shownTitle.equals(shownCompany)
                ? escape(shownTitle)
                : escape(shownTitle) + " — " + escape(shownCompany);
        return "Gerando currículo, plano de estudos e projeto para " + posting + "…";
    }

    private static String generationFailed(String reason) {
        return "Não consegui gerar agora (" + reason + "). Clique de novo mais tarde.";
    }

    /** Plain text: "title — company" on the first line, when known, and the posting's URL. */
    private static String caption(Application application) {
        String posting = Stream.of(application.title(), application.company())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" — "));
        return posting.isEmpty() ? application.url() : posting + "\n" + application.url();
    }
}
