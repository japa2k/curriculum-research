# job-search

A small Java 21 / Spring Boot 3 service that fetches job postings from LinkedIn's public,
unauthenticated "jobs-guest" endpoint and serves them as JSON. No login or API key is needed.

It is the fetch step of a future pipeline (LinkedIn → Telegram bot). See
[`specs/linkedin-guest-job-search.md`](specs/linkedin-guest-job-search.md) for the full contract.

## Requirements

- JDK 21

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

## Configuration

Set in `src/main/resources/application.yml` (or override with environment variables / `--` args,
e.g. `--linkedin.page-delay=2s`):

| Property              | Default                    | Description                     |
|-----------------------|----------------------------|---------------------------------|
| `linkedin.base-url`   | `https://www.linkedin.com` | LinkedIn host                   |
| `linkedin.user-agent` | desktop Chrome UA          | `User-Agent` sent to LinkedIn   |
| `linkedin.page-delay` | `1000ms`                   | Wait between page requests      |
| `linkedin.timeout`    | `10s`                      | HTTP timeout per request        |
