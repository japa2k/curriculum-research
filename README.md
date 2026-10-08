# job-search

A small Java 21 / Spring Boot 3 service that fetches job postings from LinkedIn's public,
unauthenticated "jobs-guest" endpoint and serves them as JSON. No login or API key is needed.

It is the fetch step of a future pipeline (LinkedIn → Telegram bot). See
[`specs/linkedin-guest-job-search.md`](specs/linkedin-guest-job-search.md) for the full contract.

On top of the raw search it recommends postings for a profile — including roles adjacent to it —
and writes study plans for them through the local Claude Code CLI. See
[`specs/profile-job-recommendations.md`](specs/profile-job-recommendations.md).

## Requirements

- JDK 21
- For study plans only: [Claude Code](https://claude.com/claude-code) installed and logged in

Maven is not required: the repo ships the Maven wrapper (`mvnw` / `mvnw.cmd`).

## Running

```sh
./mvnw spring-boot:run        # Linux / macOS / Git Bash
mvnw.cmd spring-boot:run      # Windows (cmd / PowerShell)
```

The server starts on `http://localhost:8080`. Then search:

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

Tests never hit the real LinkedIn: the parser runs against saved HTML fixtures and HTTP behavior
against a WireMock stub.

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
