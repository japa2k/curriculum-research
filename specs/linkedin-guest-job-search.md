# LinkedIn guest job search fetch
status: done
created: 2026-10-07

## Goal
Stand up a Java 21 / Spring Boot 3 service that fetches LinkedIn job postings from the public,
unauthenticated "jobs-guest" HTML endpoint and returns them as JSON through a REST endpoint. This
validates the fetch step of a future pipeline (LinkedIn → Telegram bot); "done" means a `curl` to
the endpoint returns real, correctly parsed postings for a given search.

## Context
- Files/modules this touches: greenfield — the repo holds only `README.md` and `.claude/`. The
  Maven project (`pom.xml`, `src/main/java`, `src/test/java`) goes at the repo root.
- Existing patterns/interfaces to follow: none in-repo. The `.claude/rules/*.md` files describe a
  SvelteKit project and do not apply to this Java code, except `docs.md` (specs/docs in English).
- Data source: `GET https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search`
  — public, no login, returns an HTML fragment of job cards (~10 per page), paginated with
  `start` (0, 10, 20…). Known query params: `keywords`, `location`, `f_TPR` (`r86400` = 24h,
  `r604800` = week, `r2592000` = month), `f_WT=2` (remote), `start`. Card markup as last known
  (verify against a live response when capturing the test fixture): `div.base-card` with
  `data-entity-urn="urn:li:jobPosting:<id>"`, title `h3.base-search-card__title`, company
  `h4.base-search-card__subtitle`, location `span.job-search-card__location`, date
  `time[datetime]`, link `a.base-card__full-link[href]`.
- Product & decision docs: none — decisions recorded below.
- Design & conventions: API contract defined in the acceptance criteria below.
- Tests: JUnit 5 + Spring Boot Test. Parser tests run against an HTML fixture captured from a
  real guest response, stored under `src/test/resources/`. HTTP behavior (pagination, errors) is
  tested against a WireMock stub of the LinkedIn base URL. The controller is tested with MockMvc.
  No test hits the real LinkedIn; the live check is a manual `curl`.

## Acceptance criteria
### Endpoint and parameters
- [x] The system shall expose `GET /api/jobs/search` returning `application/json`.
- [x] The system shall accept `keywords` (required, 1–100 chars after trim), `location` (optional),
  `postedWithin` (optional: `DAY` | `WEEK` | `MONTH` | `ANY`, default `ANY`), `remote` (optional
  boolean, default `false`) and `maxResults` (optional integer 1–100, default 25).
- [x] When `postedWithin` is `DAY`, `WEEK` or `MONTH`, the system shall send `f_TPR` as `r86400`,
  `r604800` or `r2592000` respectively; when `ANY`, it shall omit `f_TPR`.
- [x] When `remote` is `true`, the system shall send `f_WT=2`; otherwise it shall omit `f_WT`.
- [x] If `keywords` is missing or blank, or any parameter is outside its allowed values/range, then
  the system shall respond `400` with body `{"error": "<message naming the invalid parameter>"}`
  and shall not call LinkedIn.

### Fetching and pagination
- [x] When a valid request arrives, the system shall request pages starting at `start=0` and
  increasing `start` by the number of cards the previous page returned, until it has
  `maxResults` unique jobs, a page returns zero cards, or 10 pages have been requested.
- [x] The system shall wait at least the configured inter-page delay (default 1000 ms) between
  consecutive page requests.
- [x] The system shall send no cookies or authentication headers, and shall send a desktop-browser
  `User-Agent` taken from configuration.
- [x] The system shall read the LinkedIn base URL, `User-Agent`, inter-page delay and HTTP timeout
  (default 10 s) from `application.yml`, so tests can point the base URL at a stub.

### Parsing and output
- [x] The system shall respond `200` with body
  `{"count": <n>, "partial": <bool>, "jobs": [ ... ]}`, where each job is
  `{"id", "title", "company", "location", "url", "postedAt"}`.
- [x] The system shall take `id` as the numeric part of `urn:li:jobPosting:<id>`, and `url` as the
  card link with its query string and fragment removed.
- [x] The system shall set `postedAt` to the `time[datetime]` value as an ISO date
  (`YYYY-MM-DD`), or `null` when absent or unparseable.
- [x] The system shall trim whitespace from all text fields and set `company` and `location` to
  `null` when the element is absent or empty.
- [x] If a card has no job id or no title, then the system shall skip that card and keep the rest.
- [x] The system shall return no more than `maxResults` jobs, with no duplicate `id`s, in the order
  LinkedIn returned them.
- [x] When the first page returns zero cards, the system shall respond `200` with
  `{"count": 0, "partial": false, "jobs": []}`.

### Upstream failures
- [x] If the first page request fails (non-2xx status, including `429`, or timeout/connection
  error), then the system shall respond `502` with `{"error": "<reason incl. upstream status
  or timeout>"}`.
- [x] If a later page request fails, then the system shall stop paginating and respond `200` with
  the jobs collected so far and `"partial": true`.
- [x] If an upstream request fails, then the system shall log one WARN line with the page `start`
  and the status or exception type.

## Constraints
- Java 21, Spring Boot 3.x, Maven (with wrapper), single application at the repo root.
- HTML parsing with Jsoup; HTTP via Spring's `RestClient`.
- Guest endpoint only: no LinkedIn account, cookies, or credentials anywhere in code or config.
- No persistence and no caching — each request fetches live.
- At most 10 upstream requests per incoming API request (bounded by the pagination rule).
- Light, personal-use request volume; LinkedIn's terms prohibit scraping, so no
  aggressive paging, proxies, or block-evasion techniques.

## Out of scope
- Sending results to Telegram (next spec).
- Scheduled/automatic runs.
- Remembering already-seen jobs across requests.
- Fetching job detail pages or descriptions.
- Logged-in access (cookies / Voyager API) and any other job source.
- Auth on our own endpoint, Docker, deployment.

## Decisions & assumptions
- LinkedIn's official job APIs are partner-only → use the public jobs-guest HTML endpoint.
- Logging in with the engineer's cookie was discussed (account-ban risk, secret handling,
  cookie expiry, different JSON API) → guest only.
- Validation surface → REST `GET` endpoint returning JSON, tested by `curl`.
- Search scope → parameterized search, list fields only, `maxResults` default 25, cap 100.
- Stack → Java 21, Spring Boot 3, Maven, at the repo root.
- (Proposed, confirm) Inter-page delay 1000 ms, timeout 10 s, 10-page cap, `502` on first-page
  failure and `200` + `partial: true` on later-page failure.
- Implemented with the proposed values above (1000 ms delay, 10 s timeout, 10-page cap, `502` /
  `partial: true`), as the acceptance criteria state them.
- "Non-2xx" includes 3xx (redirects are not followed) and LinkedIn's anti-bot status `999`; both
  fail the page like a 4xx/5xx.
- Card selectors verified against a live response on 2026-10-07 (fixture
  `src/test/resources/linkedin/search-page-java-brazil.html`); the date element's class is
  sometimes `job-search-card__listdate--new`, so the parser selects `time[datetime]`.
- `location` is trimmed and omitted upstream when blank; `keywords` is sent trimmed.
