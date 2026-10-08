# job-search

A small Java 21 / Spring Boot 3 service that fetches job postings from LinkedIn's public,
unauthenticated "jobs-guest" endpoint. It does three things:

- **Telegram alerts** — every hour it runs a configured list of searches and sends each job it has
  not seen before to your Telegram chat, once. Each alert has a **📄 Gerar currículo** button that
  sends back a résumé tailored to that job, a study plan and a portfolio project.
- **REST API** — serves searches as JSON on demand. No LinkedIn login or API key is needed.
- **Recommendations** — ranks postings against your profile, including roles adjacent to it, and
  writes study plans for them through the local Claude Code CLI.

Full contracts: [`specs/linkedin-guest-job-search.md`](specs/linkedin-guest-job-search.md) (fetching
and the API), [`specs/telegram-job-alerts.md`](specs/telegram-job-alerts.md) (alerts) and
[`specs/profile-job-recommendations.md`](specs/profile-job-recommendations.md) (recommendations) and
[`specs/telegram-tailored-resume.md`](specs/telegram-tailored-resume.md) (the résumé button).

## Requirements

- JDK 21
- For study plans and the résumé button: [Claude Code](https://claude.com/claude-code) installed and
  logged in

Maven is not required: the repo ships the Maven wrapper (`mvnw` / `mvnw.cmd`).

## Setting up Telegram

1. **Create a bot.** In Telegram, open a chat with [@BotFather](https://t.me/BotFather), send
   `/newbot` and follow the prompts. BotFather replies with the bot token (`123456789:AA...`).
2. **Get your chat id.** Open a chat with your new bot and send it any message (bots cannot write to
   you first). Then open, in a browser or with curl:

   ```
   https://api.telegram.org/bot<token>/getUpdates
   ```

   The chat id is `result[].message.chat.id` in the response (a number such as `123456789`).
3. **Fill `.env`.** Copy `.env.example` to `.env` in the directory you run the app from, and set both
   values — plain `KEY=value`, no quotes:

   ```
   TELEGRAM_BOT_TOKEN=123456789:AA...
   TELEGRAM_CHAT_ID=123456789
   ```

   `.env` is gitignored; never commit it or put the token in `application.yml`. Real environment
   variables with the same names take precedence over `.env`.

The app refuses to start, naming the missing setting, if either value is blank while alerts are
enabled. To run only the REST API, turn alerts off:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--alerts.enabled=false
java -jar target/job-search-0.0.1-SNAPSHOT.jar --alerts.enabled=false
```

## Running

```sh
./mvnw spring-boot:run        # Linux / macOS / Git Bash
mvnw.cmd spring-boot:run      # Windows (cmd / PowerShell)
```

Leave it running: it runs one alert cycle right away and then one every hour (counted from the end
of the previous cycle). The server also listens on `http://localhost:8080`, for example:

```sh
curl "http://localhost:8080/api/jobs/search?keywords=java&location=Brazil&postedWithin=WEEK&maxResults=10"
```

To build a runnable jar instead:

```sh
./mvnw package
java -jar target/job-search-0.0.1-SNAPSHOT.jar
```

## Tests

```sh
./mvnw test
```

Tests never hit the real LinkedIn, Telegram or Claude CLI: the parser runs against saved HTML
fixtures, HTTP behavior against WireMock stubs, and the CLI is a fake Java process. They run in `target/test-workdir`, so they never read your
`.env`.

## API

`GET /api/jobs/search`

| Parameter      | Required | Values                              | Default |
|----------------|----------|-------------------------------------|---------|
| `keywords`     | yes      | 1–100 characters                    | —       |
| `location`     | no       | up to 100 characters                | —       |
| `postedWithin` | no       | `DAY`, `WEEK`, `MONTH`, `ANY`       | `ANY`   |
| `remote`       | no       | `true`, `false`                     | `false` |
| `maxResults`   | no       | 1–100                               | `25`    |

Response (`200`):

```json
{
  "count": 1,
  "partial": false,
  "jobs": [
    {
      "id": "4012345678",
      "title": "Java Developer",
      "company": "Acme",
      "location": "São Paulo, Brazil",
      "url": "https://www.linkedin.com/jobs/view/...",
      "postedAt": "2026-10-01"
    }
  ]
}
```

`partial` is `true` when pagination stopped early because a later page failed. Errors return
`{"error": "..."}` with `400` (invalid parameter), `502` (LinkedIn request failed) or `503`
(search interrupted).

### Recommendations

`GET /api/jobs/recommendations` runs every search in [`profile.yml`](src/main/resources/profile.yml)
against every location there (first page only), drops senior-and-above titles, reads the detail page
of up to `maxResults` postings (picked round-robin across tracks), and scores each one against the
profile's skills.

| Parameter      | Required | Values                              | Default |
|----------------|----------|-------------------------------------|---------|
| `postedWithin` | no       | `DAY`, `WEEK`, `MONTH`, `ANY`       | `WEEK`  |
| `maxResults`   | no       | 1–50                                | `30`    |

Each job adds `track` (`CORE` or the adjacent track that found it), `seniority` (`JUNIOR`, `PLENO`,
`UNKNOWN`), `descriptionAvailable`, `matchedSkills`, `missingSkills` and `category`, and the list is
ranked by category, then fewest missing skills, then newest:

| `category`  | Meaning                                              |
|-------------|------------------------------------------------------|
| `MATCH`     | no detected skill is missing                         |
| `STUDYABLE` | 1–3 skills missing — worth a study plan              |
| `STRETCH`   | more than 3 skills missing                           |
| `UNRATED`   | the description could not be read                    |

`partial` is `true` when a search or detail request failed. `502` only when every search fails.
A call makes up to 24 searches + 50 detail requests, one per `linkedin.page-delay`, so expect
roughly a minute.

### Study plan

`GET /api/jobs/{id}/study-plan` reads one posting, scores it the same way, and asks the local
[Claude Code](https://claude.com/claude-code) CLI for a week-by-week plan in Brazilian Portuguese
that fits `studyPlan` in `profile.yml` (10 h/week, at most 8 weeks). It takes tens of seconds.

```json
{
  "jobId": "4473418508",
  "title": "Desenvolvedor(a) Full Stack Master (Node.js / React)",
  "company": "Spacecom Monitoramento",
  "url": "https://www.linkedin.com/jobs/view/4473418508",
  "seniority": "PLENO",
  "matchedSkills": ["CI/CD", "JavaScript", "Node.js", "React", "TypeScript"],
  "missingSkills": ["AWS"],
  "weeklyHours": 10,
  "maxWeeks": 8,
  "plan": "# Plano de Estudo: ..."
}
```

Errors: `400` (id not 1–20 digits), `404` (LinkedIn has no such posting), `502` (detail request
failed or the page has no description), `503` (the CLI is busy with `claude-cli.max-concurrent-runs`
plans, could not start, failed, printed nothing or took longer than `claude-cli.timeout`). The CLI must be installed and logged in on this machine.

### Profile

[`src/main/resources/profile.yml`](src/main/resources/profile.yml) holds the skills you have, the
skill dictionary the matcher detects, the searches (by track) and locations, and the study budget.
Edit it and restart; the app refuses to start if it is missing or invalid (the error names the
problem). Keep contact data out of it.

## Alerts

Each cycle runs the configured searches one after another (waiting `linkedin.page-delay` between
them), drops jobs already seen, and sends one Telegram message per new job: title, company,
location, posting date and link. A job matched by several searches is sent once.

- **First run only seeds.** While the seen-jobs database is empty, a cycle records every job it
  finds and sends nothing, so you are not flooded with jobs that already existed. Only jobs that
  appear afterwards reach Telegram. Seeding is global, not per search: a search added later — or one
  that failed during the seeding cycle — sends its current matches on its next successful cycle.
- **Seen jobs** live in a SQLite file, `./data/jobs.db` by default (relative to the directory you run
  the app from; `data/` is gitignored). Delete the file to start over with a new seeding run.
- **Failures:** a failed search is logged and skipped until the next cycle. If a Telegram message
  fails, the cycle stops sending; that job and the rest stay unseen and are retried next cycle. (A
  rare duplicate is possible if Telegram accepted a message but its response was lost.)
- Each cycle logs one summary line: searches run and failed, jobs found, new jobs, messages sent.

Configure the searches and the interval in `src/main/resources/application.yml`:

```yaml
alerts:
  enabled: true
  interval: 1h
  db-path: ./data/jobs.db
  searches:
    - keywords: java
      location: Brazil
      postedWithin: DAY
    - keywords: kotlin
      remote: true
      maxResults: 50
```

Each search takes the same parameters, defaults and limits as the REST API below
(`keywords` required; `location`, `postedWithin`, `remote`, `maxResults` optional).

## Tailored résumé button

Every alert carries a **📄 Gerar currículo** button. Pressing it makes the bot read the posting, score
it against `profile.yml`, and ask the local Claude Code CLI for three documents, which it sends to
the chat and saves on disk:

| File                  | What it is                                                            |
|-----------------------|-----------------------------------------------------------------------|
| `curriculo.docx`      | Your base résumé reordered and reworded toward the posting's keywords, in the posting's language (English posting → English résumé, otherwise Portuguese). ATS-friendly: one column, no tables, images, headers or footers. Sent as `curriculo-<company>-<jobId>.docx`. |
| `plano-de-estudos.md` | A week-by-week study plan for the skills the posting asks for that you lack, within `studyPlan` in `profile.yml`. |
| `projeto.md`          | One portfolio project that exercises those skills, with talking points for the recruiter. |

The résumé keeps your name and contact data as they are and never adds an employer, title, date,
degree, certification or metric that is not in your base résumé. It does list the posting's missing
skills (the ones the study plan covers) and the project as normal entries, with the project dated
only with the current year: build them before you interview. Edit the `.docx` by hand if you want.

**Creating the base résumé.** Write your résumé in Markdown at `data/resume-base.md` (relative to the
directory you run the app from; `data/` is gitignored, so it is never committed). Use `#` for your
name, a line with your contact data, `##` for each section and `-` for items, for example:

```markdown
# Your Name
you@example.com | +55 11 90000-0000 | São Paulo, SP | linkedin.com/in/you

## Experience
### Backend Developer — Acme (2023–2025)
- Built REST APIs in Java and Spring Boot
```

While alerts are enabled the app refuses to start, naming `resume.base-path`, if this file is missing
or blank.

**How a press is handled.**

- The bot answers the press at once and replies `Gerando currículo, plano de estudos e projeto para
  <title> — <company>…`. Generating takes one CLI run (up to `claude-cli.timeout`).
- **Saved applications** go to `data/applications/<jobId>/` (the three files), plus a row in the
  `application` table of `alerts.db-path` (job id, title, company, URL, generation time). Pressing the
  button again for that job resends the saved files without running the CLI or calling LinkedIn; if a
  saved file was deleted, the job is generated again. Delete the folder to regenerate on purpose.
- Only presses from `TELEGRAM_CHAT_ID` are honored; anything from another chat is ignored and logged
  as one WARN line.
- A second press while a job is still generating gets `Já estou gerando esse currículo, aguarde.`
- Failures are replied in the chat and save nothing: the posting is gone (LinkedIn 404), LinkedIn
  could not be read, the CLI is busy (`claude-cli.max-concurrent-runs` runs already going — there is
  no queue, press again later) or failed. If sending a file fails, the saved files stay and the next
  press resends them.
- Presses are read by long-polling Telegram's `getUpdates`, so no public URL or webhook is needed.
  Do not run two copies of the app with the same bot: Telegram hands each press to only one of them.

## Configuration

Set in `src/main/resources/application.yml` (or override with environment variables / `--` args,
e.g. `--linkedin.page-delay=2s`):

| Property              | Default                    | Description                     |
|-----------------------|----------------------------|---------------------------------|
| `linkedin.base-url`   | `https://www.linkedin.com` | LinkedIn host                   |
| `linkedin.user-agent` | desktop Chrome UA          | `User-Agent` sent to LinkedIn   |
| `linkedin.page-delay` | `1000ms`                   | Wait between page requests      |
| `linkedin.timeout`    | `10s`                      | HTTP timeout per request        |
| `profile.location`    | `classpath:profile.yml`    | Profile file (any Spring resource) |
| `claude-cli.command`  | `claude`                   | CLI executable; on Windows a bare name also finds `claude.cmd`/`.exe` on the PATH |
| `claude-cli.args`     | `-p --output-format text --tools "" --strict-mcp-config --no-session-persistence` | Fixed arguments; tools and MCP servers are off because job descriptions are untrusted |
| `claude-cli.timeout`  | `180s`                     | Kill the CLI after this long    |
| `claude-cli.max-concurrent-runs` | `2`             | CLI processes allowed at once; further study-plan requests get `503` |
| `alerts.enabled`      | `true`                     | Run the hourly Telegram alerts  |
| `alerts.interval`     | `1h`                       | Wait after a cycle ends         |
| `alerts.db-path`      | `./data/jobs.db`           | SQLite file of seen jobs        |
| `alerts.searches`     | one example search         | Searches run each cycle         |
| `telegram.base-url`   | `https://api.telegram.org` | Telegram Bot API host           |
| `resume.base-path`    | `./data/resume-base.md`    | Your base résumé (Markdown); required while alerts are enabled |
| `resume.applications-dir` | `./data/applications`  | Where each job's generated files are saved |
