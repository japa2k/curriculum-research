# Telegram alerts for new LinkedIn jobs
status: done
created: 2026-10-07

## Goal
Turn the job-search service into a long-running process that, every hour, runs a configured list
of LinkedIn searches, discards jobs it has already seen (tracked in a local SQLite file) and sends
each new job to the engineer's Telegram chat. "Done" means: leave the app running, and a job that
appears on LinkedIn for a configured search arrives in Telegram exactly once.

## Context
- Files/modules this touches: `pom.xml`, `src/main/resources/application.yml`, `.gitignore`,
  `README.md`, new code under `src/main/java/io/github/luccastk/jobsearch/` (new subpackages, e.g.
  `alerts/` and `telegram/`, mirroring the existing `linkedin/`), new `.env.example` at the repo root.
- Existing patterns/interfaces to follow: reuse `JobSearchService.search(SearchQuery)` as-is for
  fetching (pagination, `partial`, `UpstreamException` on first-page failure). Follow
  `LinkedInGuestClient` / `LinkedInProperties` for the Telegram client (Spring `RestClient`,
  `@ConfigurationProperties`, configurable base URL so tests can stub it). `JobPosting` is the job
  shape; `id` is the dedup key.
- Previous spec: `specs/linkedin-guest-job-search.md` (fetch step; its "Out of scope" listed
  Telegram, scheduling and remembering seen jobs — this spec covers exactly those).
- Telegram Bot API: `POST https://api.telegram.org/bot<token>/sendMessage` with `chat_id`, `text`,
  `parse_mode=HTML`. Success is HTTP 200 with `{"ok": true, ...}`. Text limit 4096 chars.
  Rate limit ~1 message/second per chat; `429` carries `parameters.retry_after`.
- Product & decision docs: none — decisions recorded below.
- Tests: JUnit 5 + Spring Boot Test, as in the existing suite. Telegram calls tested against a
  WireMock stub of the Telegram base URL; LinkedIn against the existing WireMock approach. The
  seen-jobs store tested against a SQLite file in a JUnit `@TempDir`. The scheduling cycle is
  tested by invoking the cycle method directly (no real waiting for the hourly trigger). No test
  hits real LinkedIn or real Telegram; the live check is manual (run the app with a real `.env`).

## Acceptance criteria
### Configuration and secrets
- [x] The system shall read `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` from a `.env` file at the
  working directory (optional file, `KEY=value` lines), with real environment variables taking
  precedence over `.env`.
- [x] The repo shall contain a committed `.env.example` listing `TELEGRAM_BOT_TOKEN=` and
  `TELEGRAM_CHAT_ID=` with empty values, and `.gitignore` shall ignore `.env` and the SQLite
  data directory.
- [x] The system shall read from `application.yml`: the alert interval (default `1h`), the
  Telegram base URL (default `https://api.telegram.org`), the SQLite file path (default
  `./data/jobs.db`), and a list of searches, each with `keywords`, `location`, `postedWithin`,
  `remote` and `maxResults` using the same names, defaults and validation as the REST endpoint.
- [x] The shipped `application.yml` shall contain one example search (`keywords: java`,
  `location: Brazil`, `postedWithin: DAY`).
- [x] If alerts are enabled and `TELEGRAM_BOT_TOKEN` or `TELEGRAM_CHAT_ID` is blank, or the search
  list is empty, or any search fails validation, then the system shall fail at startup with an
  error naming the missing/invalid setting.
- [x] Where `alerts.enabled` is `false`, the system shall not schedule cycles and shall not require
  the Telegram settings (the REST endpoint keeps working).
- [x] The system shall never write the bot token to logs, including inside exception messages or
  request URLs that get logged.

### Scheduling
- [x] When the application starts with alerts enabled, the system shall run one cycle immediately
  and then start the next cycle one interval after the previous cycle finished (no overlapping
  cycles).
- [x] During a cycle, the system shall run every configured search sequentially, waiting at least
  `linkedin.page-delay` between consecutive searches.
- [x] If a search fails (`UpstreamException`, `SearchInterruptedException` or any other
  exception), then the system shall log one WARN line naming the search's keywords and the
  reason, and continue with the next search.
- [x] If a cycle throws unexpectedly, then the system shall log it at ERROR and still run the next
  scheduled cycle.
- [x] The system shall log one INFO line per cycle with: searches run, searches failed, jobs found,
  new jobs, messages sent.

### Seen-jobs store (SQLite)
- [x] The system shall persist seen jobs in a SQLite file at the configured path, creating the
  parent directory and a single table `seen_job(job_id TEXT PRIMARY KEY, first_seen_at TEXT NOT
  NULL)` (ISO-8601 UTC instant) if absent.
- [x] The system shall treat a job as new only when its `id` is not in `seen_job`.
- [x] The system shall deduplicate jobs by `id` across all searches within one cycle, so a job
  matched by two searches is sent at most once.
- [x] Seen jobs shall persist across restarts and shall never be deleted automatically.

### First run (seeding)
- [x] When a cycle starts and `seen_job` is empty, the system shall record every job found in that
  cycle as seen without sending any Telegram message, and log one INFO line with the number of
  jobs seeded.
- [x] If every search failed during a seeding cycle (zero jobs found), then `seen_job` stays empty
  and the next cycle is again a seeding cycle.

### Sending to Telegram
- [x] When a cycle (not seeding) finds new jobs, the system shall send one `sendMessage` per new
  job to the configured chat, in the order found (searches in config order, then LinkedIn order).
- [x] Each message shall use `parse_mode=HTML` and contain, one per line: the title in bold, the
  company, the location, `Posted: <YYYY-MM-DD>`, and the job URL; a line whose field is `null` is
  omitted. Title, company and location shall be HTML-escaped (`&`, `<`, `>`).
- [x] The system shall send messages with link previews disabled.
- [x] The system shall wait at least 1 second between consecutive `sendMessage` calls.
- [x] When a `sendMessage` call succeeds, the system shall record that job as seen immediately
  (before sending the next).
- [x] If a `sendMessage` call fails (non-2xx, `ok: false`, timeout or connection error), then the
  system shall not mark that job as seen, shall log one WARN line with the job id and HTTP status
  or exception type, and shall stop sending for the rest of the cycle (unsent jobs remain unseen
  and are retried next cycle).

### REST endpoint
- [x] The existing `GET /api/jobs/search` shall keep its current behavior and shall not read or
  write the seen-jobs store.

### Docs
- [x] `README.md` shall document: creating a bot with @BotFather, obtaining the chat id (send the
  bot a message, then `GET https://api.telegram.org/bot<token>/getUpdates`), filling `.env`,
  configuring the search list and interval, where the SQLite file lives, and that the first run
  only seeds.

## Constraints
- Java 21, Spring Boot 3.x, Maven wrapper — same single application at the repo root.
- SQLite via the `org.xerial:sqlite-jdbc` driver and Spring `JdbcTemplate`; no JPA/Hibernate, no
  migration tool (schema created with `CREATE TABLE IF NOT EXISTS`).
- Telegram via Spring `RestClient`, no Telegram SDK dependency; HTTP timeout 10 s.
- Scheduling with Spring's `@Scheduled` / scheduler — no external cron, queue or extra service.
- LinkedIn request volume stays light: hourly cycles, searches sequential, existing per-search
  10-page cap and page delay unchanged. No proxies or block-evasion.
- Secrets live only in `.env` / environment variables — never in `application.yml`, code, tests
  or commits.

## Out of scope
- Bot commands or any inbound Telegram handling (webhook, polling, buttons).
- Multiple chats/recipients.
- Filtering beyond LinkedIn's search params (e.g. keyword exclusions, salary, seniority).
- Fetching job descriptions/detail pages.
- Running as an OS service, Docker, or deployment — "runs nonstop" means the process stays up
  with `mvnw spring-boot:run` / `java -jar` on the engineer's machine.
- Pruning the seen-jobs table, or storing anything beyond `job_id` and `first_seen_at`.
- Honoring Telegram `retry_after` beyond stopping the cycle (next cycle retries).

## Decisions & assumptions
- Search source and interval → list of searches in `application.yml`, every 1 hour.
- Database → SQLite file (`./data/jobs.db`).
- First run → seed only: record everything found, send nothing; only jobs that appear afterwards
  go to Telegram.
- Message format → one Telegram message per job (title, company, location, date, link).
- Chat target → engineer's personal chat, id in `.env` as `TELEGRAM_CHAT_ID` alongside
  `TELEGRAM_BOT_TOKEN`.
- (Proposed, confirm) A job is marked seen only after its message is sent successfully; on a send
  failure the cycle stops sending and the rest retry next cycle (no job is lost, none is sent twice
  except if Telegram accepted a message but the response was lost).
- (Proposed, confirm) Seeding is global (empty table), not per search: a search added later sends
  its current matches on its first cycle.
- (Proposed, confirm) A failed search is skipped for that cycle; jobs it would have returned are
  picked up next cycle (deduplication makes this safe).
- (Proposed, confirm) Startup fails fast on missing Telegram settings when alerts are enabled;
  `alerts.enabled` (default `true`) exists so tests and REST-only runs can turn alerts off.
- A local `.env` (gitignored, empty values) was created in this worktree at spec time so the
  engineer can fill in the token and chat id now.
- (Implementation) The "(Proposed, confirm)" items above were implemented as proposed.
- (Implementation) `.env` is loaded with `spring.config.import: optional:file:.env[.properties]`, so it
  uses Java properties syntax: plain `KEY=value`, no quotes or `export`. Environment variables win
  because Spring ranks them above imported config files.
- (Implementation) Validation is shared: `SearchQuery.of(...)` (moved out of the controller) validates
  both REST parameters and configured searches; config errors are reported as
  `alerts.searches[<i>].<field> ...`. `remote` is bound as a string so it gets the same strict
  `true`/`false` check as the endpoint.
- (Implementation) The job URL is HTML-escaped too, not only title/company/location: Telegram's HTML
  mode rejects a bare `&` anywhere in the text.
- (Implementation) Link previews are disabled with `link_preview_options: {is_disabled: true}`.
- (Implementation) The Telegram client forces HTTP/1.1 and uses component-level URI encoding so the
  `:` in the token stays literal. `TelegramException` never carries a cause (Spring's I/O exceptions
  quote the URL, which contains the token), and `TelegramProperties.toString()` masks the token.
- (Implementation) The seen-jobs store builds its own `SQLiteDataSource` + `JdbcTemplate` (only
  `spring-jdbc`, no starter/pool), so no DataSource exists and nothing touches SQLite when
  `alerts.enabled=false`.
- (Implementation) Scheduling is a `SchedulingConfigurer` fixed-delay task (initial delay 0) driven by
  `alerts.interval`; cycle exceptions are caught and logged at ERROR so the schedule continues.
- (Implementation) In a seeding cycle the summary line reports `newJobs` = jobs seeded and
  `messagesSent=0`. An interrupt (application shutdown) ends the cycle at its next pause.
- (Implementation) Surefire runs tests in `target/test-workdir` with a fake `TELEGRAM_CHAT_ID`
  environment variable: tests never read the real `.env`, and one test proves env-over-`.env`
  precedence for real.
