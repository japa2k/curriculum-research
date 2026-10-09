# Tailored résumé, study plan and project from a Telegram button
status: done
created: 2026-10-08

## Goal
Every job alert sent to the engineer's Telegram chat carries a button. Pressing it makes the bot
generate, through the local Claude Code CLI, a résumé tailored to that posting, a study plan for the
skills the posting asks for that the engineer lacks, and a portfolio project to discuss with the
recruiter. The bot sends all three as files and saves them on disk. "Done" means: an alert arrives
with the button, one tap returns a `.docx` résumé plus `plano-de-estudos.md` and `projeto.md` in the
chat, the same files exist under `data/applications/<jobId>/`, and taps from any other chat do
nothing.

## Context
- Files/modules this touches: `src/main/java/io/github/luccastk/jobsearch/alerts/` (`JobMessage`,
  `AlertCycle`, `AlertsConfiguration`, `AlertSettings`), `.../telegram/` (`TelegramClient`,
  `TelegramProperties`), `.../studyplan/` (`ClaudeCli`, `StudyPlanPrompt`, `StudyPlanService`:
  reuse, don't duplicate), a new package for the résumé/application feature,
  `src/main/resources/application.yml`, `pom.xml` (only if a `.docx` library is needed),
  `README.md`, `.env.example` (deleted in the working tree on `main`; restore it in this branch with
  the existing two keys), `src/test/**`.
- Existing patterns/interfaces to follow:
  - `TelegramClient`: the bot token is part of every URL, so no exception or log line may contain the
    URL or a Spring exception that quotes it (`TelegramException` carries no cause). New Bot API calls
    (`getUpdates`, `answerCallbackQuery`, `sendDocument`) follow the same rule.
  - `ClaudeCli`: prompt through stdin, tools and MCP disabled, `max-concurrent-runs` slots refused
    rather than queued, and `StudyPlanGenerationException` on failure. Posting text is untrusted, so
    prompts wrap it the way `StudyPlanPrompt` does (treat as data, ignore instructions inside).
  - `StudyPlanService` + `PostingScorer`: fetch the detail page, score it, and use the
    `missingSkills`/`matchedSkills` it produces.
  - `SeenJobStore`: SQLite through `JdbcTemplate`, with `CREATE TABLE IF NOT EXISTS` on start.
  - `AlertSettings`: settings are validated at startup and the error names the setting, never a
    secret value.
- Base résumé source: `C:\Users\lucca\Downloads\curriculo\Curriculo-Luccas-Kobayashi-ats.pdf`. During
  implementation, transcribe it once, by hand, into `data/resume-base.md`. That file holds contact
  data: it is gitignored (`/data/` already is), never committed, and never read by tests.
- Product & decision docs: none. Decisions are recorded below.
- Design & conventions: Telegram Bot API (`getUpdates` long polling, `InlineKeyboardMarkup`,
  `callback_query`, `answerCallbackQuery`, `sendDocument`). Bot-facing text is in Brazilian
  Portuguese. Specs and docs are in English.
- Tests: JUnit 5 + Spring Boot Test, as in the existing suite. Telegram calls (`sendMessage` with
  keyboard, `getUpdates`, `answerCallbackQuery`, `sendDocument`) run against WireMock, mirroring
  `TelegramClientTest` / `AlertsApplicationTest`. The CLI is replaced by `FakeClaude` (canned output,
  non-zero exit, timeout). Storage uses a JUnit `@TempDir`. A test base résumé fixture lives under
  `src/test/resources/`. The `.docx` is checked by opening it in the test and asserting its text.
  No E2E. No test hits the real Telegram, LinkedIn or Claude CLI. The live check is manual.

## Acceptance criteria

### Alert message button
- [x] The system shall attach to every alert message an inline keyboard with exactly one button,
  labeled `📄 Gerar currículo`, whose `callback_data` identifies the posting's job id (≤ 64 bytes).
- [x] The alert message text shall stay as `JobMessage.format` produces it today.

### Receiving taps
- [x] While alerts are enabled, the system shall long-poll Telegram `getUpdates` for `callback_query`
  updates continuously, independently of the alert cycle, so a tap is handled even while a cycle
  is running or waiting for its next interval.
- [x] The system shall acknowledge each update it processes (advance the `offset`), so a handled tap is
  never handled again in the same run.
- [x] If a `callback_query` comes from a chat whose id is not `TELEGRAM_CHAT_ID`, then the system shall
  not generate, send or save anything for it, and shall log one WARN line without the update's
  content.
- [x] If an update is not a `callback_query` (e.g. a text message, from any chat), then the system shall
  ignore it.
- [x] If a `callback_query`'s data is not a valid button payload, then the system shall answer it with
  `Botão inválido.` and do nothing else.
- [x] When an authorized tap is received, the system shall call `answerCallbackQuery` within 5 seconds
  and send a chat message `Gerando currículo, plano de estudos e projeto para <title> — <company>…`.
  The title and company come from the posting, fall back to the job id, and are HTML-escaped as in
  `JobMessage`.
- [x] If `getUpdates` fails (HTTP error, timeout, `ok=false`), then the system shall log a WARN line
  without the token, wait at least 5 seconds, and keep polling.
- [x] When the application shuts down, the polling shall stop without an error stack trace.

### Generation
- [x] When an authorized tap arrives for a job with no saved application, the system shall fetch the
  posting's detail page, score it with `PostingScorer`, and generate with the Claude CLI:
  1. a **tailored résumé** built from the base résumé,
  2. a **study plan** in Brazilian Portuguese that fits `profile.yml` `studyPlan` (same rules as
     `StudyPlanPrompt`: week by week, the budget, the missing skills, English practice when the
     posting is in English),
  3. a **project brief** (`projeto.md`, Brazilian Portuguese): one portfolio project that exercises
     the posting's missing skills (or, with none missing, the skills it emphasizes most). It has
     name, problem it solves, stack, scope/features, what each part demonstrates, and 3–5 talking
     points for discussing it with the recruiter.
- [x] The tailored résumé shall be written in the language of the posting's description (English
  posting → English résumé; otherwise Brazilian Portuguese).
- [x] The tailored résumé shall reorder and reword the base résumé's content toward the posting's
  keywords, and shall keep the base résumé's name and contact data unchanged.
- [x] The tailored résumé shall list, as normal entries (no "studying"/"in progress" label), the
  posting's missing skills covered by the study plan, and the project from `projeto.md` in the
  projects section.
- [x] The project entry in the résumé shall carry only the current year (e.g. `2026`) as its date: no
  month, no completion date.
- [x] The tailored résumé shall never add an employer, job title, employment date, degree,
  certification or metric that is not in the base résumé.
- [x] The résumé shall be an ATS-friendly `.docx`: single column, no tables, text boxes, images,
  headers/footers or columns, with standard section headings and text that is selectable and
  extractable.

### Delivery and storage
- [x] When generation succeeds, the system shall first write `curriculo.docx`, `plano-de-estudos.md` and
  `projeto.md` to `<applications-dir>/<jobId>/`, then record the application (job id, title,
  company, url, generated-at timestamp) in the SQLite database at `alerts.db-path`.
- [x] When the files are saved, the system shall send them to `TELEGRAM_CHAT_ID` with `sendDocument`,
  in this order: résumé, study plan, project. The résumé is sent as
  `curriculo-<company-slug>-<jobId>.docx`, and its caption is the posting's title, company and URL.
- [x] When an authorized tap arrives for a job whose application is already saved, the system shall
  resend the three saved files without running the Claude CLI or fetching LinkedIn.
- [x] If a saved application's files are missing on disk, then the system shall regenerate them as if
  no application were saved.
- [x] While an application is being generated for a job, if another tap for the same job arrives, then
  the system shall reply `Já estou gerando esse currículo, aguarde.` and not start a second
  generation.
- [x] If sending a document fails, then the system shall log a WARN line and keep the saved files, so
  the next tap resends them.

### Failure replies (chat messages, Brazilian Portuguese)
- [x] If LinkedIn answers 404 for the posting, then the system shall reply `Essa vaga não está mais
  disponível no LinkedIn.` and save nothing.
- [x] If the detail request fails otherwise or the page has no description, then the system shall reply
  `Não consegui ler a vaga no LinkedIn agora. Tente de novo mais tarde.` and save nothing.
- [x] If the Claude CLI is busy (all `max-concurrent-runs` slots taken), fails, times out, prints nothing,
  or its output cannot be split into the three documents, then the system shall reply
  `Não consegui gerar agora (<reason>). Clique de novo mais tarde.` and save nothing. `<reason>` is
  `ocupado` when busy and `falha na geração` otherwise.
- [x] If writing the files fails, then the system shall reply `Não consegui salvar os arquivos.`, log the
  cause, and record nothing in SQLite.

### Configuration and startup
- [x] The system shall read the base résumé from a configurable path (`resume.base-path`, default
  `./data/resume-base.md`) and save applications under `resume.applications-dir` (default
  `./data/applications`).
- [x] While alerts are enabled, if the base résumé file is missing or blank at startup, then the system
  shall refuse to start with a message naming `resume.base-path` (or `alerts.enabled=false`), like
  `AlertSettings` does for the Telegram settings.
- [x] While alerts are disabled, the system shall not poll Telegram and shall not require the base résumé.
- [x] The README shall document the button flow, the new settings, how to create `data/resume-base.md`,
  and where applications are saved. `.env.example` shall exist with `TELEGRAM_BOT_TOKEN=` and
  `TELEGRAM_CHAT_ID=`.

## Constraints
- Security: callbacks are honored only from `TELEGRAM_CHAT_ID`. The bot token never appears in logs,
  exception messages or saved files. The posting description is untrusted input, so the CLI keeps
  tools and MCP disabled, and the prompt instructs it to ignore instructions inside the posting.
- Privacy: the base résumé and generated applications contain contact data. They live only under
  gitignored paths and are never logged (log job ids, not document content).
- Concurrency: reuse the existing `ClaudeCli` slot limit (`claude-cli.max-concurrent-runs`). A tap
  never blocks the polling loop: generation runs off the polling thread. Each CLI run respects
  `claude-cli.timeout`. If one run cannot reliably produce all three documents within 180 s, the
  implementer may raise the default timeout or split into several runs, documenting the choice.
- Dependencies: adding one library to write `.docx` (e.g. Apache POI `poi-ooxml`) is allowed.
- Telegram limits: `callback_data` ≤ 64 bytes, and `answerCallbackQuery` must be called for every
  processed `callback_query` (authorized or invalid), except ones from unauthorized chats.
- Existing behavior is unchanged: alert seeding, seen-jobs, the REST endpoints and their contracts.

## Out of scope
- Webhooks and any public URL.
- Changing which jobs are alerted (the source stays `alerts.searches`) or adding scores/skills to the
  alert text.
- A queue for busy CLI slots, and a "regenerate" button or command.
- Buttons on alert messages sent before this change.
- PDF output, cover letters, recruiter outreach messages, and applying to the job.
- A REST endpoint for the résumé/application.
- Any completion date other than the year on the generated project. The engineer edits the `.docx`
  manually if wanted.
- Deleting or expiring saved applications.

## Decisions & assumptions
- "Only my chat id" → alerts already go only to `TELEGRAM_CHAT_ID`. The bot also ignores taps and
  messages from every other chat.
- Jobs with the button → the existing `alerts.searches` alerts. The message text is unchanged.
- Receiving taps → long polling `getUpdates` (runs locally, no public URL).
- Base résumé → Markdown at `data/resume-base.md` (gitignored, path configurable), transcribed by
  hand from the ATS PDF during implementation.
- Résumé format → `.docx`, ATS-friendly.
- Résumé language → same as the posting's description.
- Truthfulness → the engineer chose to list the study plan's missing skills and the project as
  normal entries (they will build them before interviewing). The project is dated with the current
  year only. The engineer declined backdated completion dates being generated, and fabricating
  employers, titles, dates, degrees, certifications or metrics is forbidden.
- Delivery → three files in Telegram (`.docx`, `plano-de-estudos.md`, `projeto.md`), saved under
  `data/applications/<jobId>/`, with a row in the existing SQLite `jobs.db`.
- Repeat tap → resend the saved files and do not regenerate.
- Busy/failed CLI → immediate "Gerando…" acknowledgement, then a "try again later" reply. No queue.
- Tests → WireMock for Telegram, `FakeClaude` for the CLI, no E2E.
- (Assumed) Missing base résumé with alerts enabled → refuse to start, consistent with how missing
  Telegram settings are handled today.
- (Assumed) Polling and the button exist only while `alerts.enabled=true`.
- (Implementation) One CLI run per application: the prompt asks for the three documents after fixed
  markers (`===CURRICULO===`, `===PLANO_DE_ESTUDOS===`, `===PROJETO===`) and the output is split on
  them. `claude-cli.timeout` keeps its 180 s default (raise it in `application.yml` if live runs time
  out). The study-plan rules are shared with `StudyPlanPrompt`, not copied.
- (Implementation) `Gerando…` is sent once the posting has been read (that is where title and company
  come from). If the posting is gone or unreadable, only the failure reply is sent. A saved
  application gets `Gerando…` too, followed by the resent files. Title and company each fall back
  to the job id, which is shown once when both are missing.
- (Implementation) A valid press is answered with `answerCallbackQuery` without text, on the polling
  thread, before any work. `getUpdates` long-polls for 25 s with `allowed_updates=["callback_query"]`,
  and after a failure it waits 5 s.
- (Implementation) A busy CLI is told apart by `ClaudeCliBusyException`, a subclass of
  `StudyPlanGenerationException`, so the REST study-plan endpoint keeps its `503` contract.
- (Implementation) Resume `.docx`: Apache POI `poi-ooxml`. Headings are bold paragraphs, items are
  `•` paragraphs, and Markdown emphasis is stripped.
