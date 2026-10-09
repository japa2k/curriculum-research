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
| `study-plan.max-weeks`    | `8`                        | Longest plan, in weeks                    |
