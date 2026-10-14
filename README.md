# curriculum-research

Spring Boot 4.1 web application (Java 21, Maven).

## Requirements

- JDK 21+ (`JAVA_HOME` must point to it)
- No Maven install needed — use the included wrapper (`mvnw` / `mvnw.cmd`)

## Run the app

```bash
./mvnw spring-boot:run        # Git Bash / macOS / Linux
.\mvnw.cmd spring-boot:run    # PowerShell / CMD
```

The app starts on http://localhost:8080. Stop it with `Ctrl+C`.

Or build a jar and run it:

```bash
./mvnw package
java -jar target/curriculum-research-0.0.1-SNAPSHOT.jar
```

## Run the tests

```bash
./mvnw test
```

Run a single class or method:

```bash
./mvnw test -Dtest=CurriculumResearchApplicationTests
./mvnw test -Dtest=CurriculumResearchApplicationTests#contextLoads
```

Test reports are written to `target/surefire-reports/`.

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

Settings live in `src/main/resources/application.properties`. Local secrets go in `.env`, which is git-ignored.

## Tailored résumé and study plan per job

Every job the bot sends comes with two PDFs: a résumé tailored to that job and a study plan for it.
Right before a job is sent, the app:

1. reads the posting's full description from LinkedIn's public posting page and stores it in the
   `job_descriptions` table (read once per job);
2. opens the local [Claude Code](https://claude.com/claude-code) CLI (`claude -p`, no tools or MCP
   servers, since the posting text is untrusted) with the base résumé and the description, and closes
   it when the answer is in — about a minute or two per job;
3. renders both documents to PDF (single column, selectable text, ATS-friendly) and saves them in
   `data/applications/<job id>/`;
4. sends the job, then `curriculo-<company>.pdf` and `plano-de-estudos-<company>.pdf`, to the chat.

The résumé only reorders and rewords what is in the base résumé; it is written in the posting's
language. If the description cannot be read or Claude fails, the job is still sent, with a notice.
A job whose message failed to send reuses the PDFs already on disk on the next delivery.

Setup:

- Claude Code installed and logged in on the machine running the app (`claude` on the PATH, or set
  `claude.command`).
- The base résumé at `data/curriculo-base.html` (any HTML or text with your résumé; only the content
  is used). `data/` is git-ignored because it holds contact data.

| Property                  | Default                    | Description                               |
|---------------------------|----------------------------|-------------------------------------------|
| `resume.base-path`        | `data/curriculo-base.html` | Base résumé, read for every job           |
| `applications.dir`        | `data/applications`        | Where the PDFs are saved                  |
| `claude.command`          | `claude`                   | Claude Code CLI executable                |
| `claude.timeout`          | `5m`                       | Kill the CLI after this long              |
| `study-plan.weekly-hours` | `10`                       | Study hours per week the plan assumes     |
| `study-plan.max-weeks`    | `4`                        | Longest plan, in weeks                    |
