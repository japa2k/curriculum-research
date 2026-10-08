# Profile-based job recommendations and study plans
status: done
created: 2026-10-07

## Goal
Extend the job-search service so it searches LinkedIn on behalf of the engineer's profile — both
roles the résumé already covers and adjacent roles it does not — scores each posting against the
profile's skills, and classifies it as a direct match, a role the engineer can study for, or a
stretch. For a chosen posting, the service generates a study plan through the locally installed
Claude Code CLI. "Done" means one `curl` returns a ranked, classified list of real postings with
matched/missing skills, and a second `curl` returns a study plan for one of them.

## Context
- Files/modules this touches: `src/main/java/io/github/luccastk/jobsearch/**` (new endpoints,
  services), `src/main/java/io/github/luccastk/jobsearch/linkedin/` (new detail-page client +
  parser), `src/main/resources/` (new `profile.yml`, `application.yml` keys), `src/test/**`,
  `README.md` (document the new endpoints and config).
- Existing patterns/interfaces to follow: `LinkedInGuestClient` (RestClient, configured base URL,
  User-Agent, inter-page delay, timeout, WARN log on upstream failure), `JobCardParser` (Jsoup,
  trimmed fields, null on absent), `ApiExceptionHandler` / `ApiError` (`{"error": "..."}` bodies),
  `LinkedInProperties` (`@ConfigurationProperties`), `JobSearchService` (search pagination).
  Reuse the existing search fetch; do not duplicate it.
- Source of the profile: `C:\Users\lucca\Downloads\curriculo\Curriculo-Luccas-Kobayashi-ats.pdf`
  (read once, by hand, to seed `profile.yml`; never read at runtime, never committed).
- Detail data source: `GET https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{id}` — public,
  no login, returns an HTML fragment. Last known markup (verify against a live response when
  capturing the fixture): description in `div.show-more-less-html__markup`; criteria in
  `li.description__job-criteria-item`, each with an `h3.description__job-criteria-subheader`
  (e.g. "Seniority level") and `span.description__job-criteria-text` (e.g. "Entry level",
  "Associate", "Mid-Senior level").
- Study-plan generator: Claude Code CLI installed on the engineer's machine, invoked
  non-interactively (print mode). No Anthropic API key.
- Product & decision docs: none — decisions recorded below.
- Design & conventions: API contracts defined in the acceptance criteria below. Specs/docs in
  English.
- Tests: JUnit 5 + Spring Boot Test, like `JobSearchApiTest` / `JobCardParserTest`. Detail parser
  tested against an HTML fixture captured from a real `jobPosting/{id}` response under
  `src/test/resources/linkedin/`. Upstream search + detail behavior tested against WireMock.
  Skill matching and classification tested as plain unit tests. The Claude CLI is replaced in tests
  by a configured fake command (a small script under `src/test/resources/`) that echoes canned
  output, exits non-zero, or sleeps past the timeout. Controllers tested with MockMvc. No test hits
  the real LinkedIn or the real Claude CLI; the live check is a manual `curl`.

## Acceptance criteria

### Profile
- [x] The system shall load the profile from `src/main/resources/profile.yml` at startup,
  containing: `knownSkills` (skill names the engineer has), `skillDictionary` (every skill the
  matcher can detect, each with a `name`, a list of `aliases`, and an optional
  `caseSensitive: true`), `searches` (each a `keywords` string and a `track` name, where track
  `CORE` means a role the résumé already covers and any other value names an adjacent track),
  `locations` (each a LinkedIn `location` string and a `remote` boolean), and `studyPlan`
  (`weeklyHours`, `maxWeeks`).
- [x] The committed `profile.yml` shall be seeded from the résumé PDF: `knownSkills` lists the
  résumé's technical skills; `searches` includes at least one `CORE` search for the fullstack
  Next.js/NestJS profile and at least one for Java/Spring backend, plus at least one search for
  each adjacent track: `BACKEND_JVM` (e.g. Kotlin/Java backend), `PLATFORM_DEVOPS`,
  `APPSEC_DEVSECOPS`, `AI_ENGINEER`, `MOBILE_REACT_NATIVE`; `locations` covers remote Brazil,
  on-site/hybrid Grande São Paulo, and remote international (English-language) postings;
  `studyPlan` is `weeklyHours: 10`, `maxWeeks: 8`.
- [x] The committed `profile.yml` shall contain no phone number, e-mail address, or street
  address.
- [x] The `skillDictionary` shall contain every `knownSkills` entry plus the skills each adjacent
  track's postings typically require (e.g. Kotlin, Go, AWS, Kubernetes, Terraform, GraphQL,
  React Native, OWASP, SAST/DAST, LangChain, RAG, vector databases), so missing skills can be
  detected.
- [x] If `profile.yml` is missing or invalid (no `searches`, no `locations`, a `knownSkills` entry
  absent from `skillDictionary`, `searches × locations` greater than 24, or `weeklyHours`/
  `maxWeeks` not positive), then the application shall fail to start with an error naming the
  problem.

### Recommendations endpoint
- [x] The system shall expose `GET /api/jobs/recommendations` returning `application/json`, with
  optional parameters `postedWithin` (`DAY` | `WEEK` | `MONTH` | `ANY`, default `WEEK`) and
  `maxResults` (integer 1–50, default 30).
- [x] If a parameter is outside its allowed values/range, then the system shall respond `400` with
  `{"error": "<message naming the invalid parameter>"}` and shall not call LinkedIn.
- [x] When a valid request arrives, the system shall run one search per (`searches` entry ×
  `locations` entry) pair, fetching only the first page (`start=0`) of each, with the given
  `postedWithin`, and the pair's `remote` flag.
- [x] The system shall merge search results by job `id`, keeping the first occurrence's `track`
  in profile order (searches in file order, then locations in file order).
- [x] The system shall drop postings whose title marks them as senior or above (case-insensitive
  whole word: `senior`, `sênior`, `sr`, `lead`, `líder`, `staff`, `principal`, `specialist`,
  `especialista`, `manager`, `gerente`, `head`, `director`, `diretor`) before fetching details.
- [x] The system shall select at most `maxResults` remaining postings round-robin across tracks —
  tracks in the order they first appear in `searches`, postings within a track in merge order;
  round 1 takes each track's first posting, round 2 each track's second, and so on — and fetch
  their detail pages in that selection order.
- [x] The system shall wait at least the configured inter-page delay between every two consecutive
  upstream requests (searches and details alike).
- [x] The system shall respond `200` with
  `{"count": <n>, "partial": <bool>, "jobs": [ ... ]}`, where each job is
  `{"id", "title", "company", "location", "url", "postedAt", "track", "seniority",
  "descriptionAvailable", "matchedSkills", "missingSkills", "category"}`; the first six fields
  have the same meaning as in `GET /api/jobs/search`.
- [x] The system shall set `seniority` from the title first — `JUNIOR` for whole words `junior`,
  `júnior`, `jr`, `estágio`, `estagiário`, `intern`, `trainee`; `PLENO` for `pleno`, `pl`, `mid`,
  `mid-level` — and otherwise from the detail page's "Seniority level" criterion — `JUNIOR` for
  "Internship" or "Entry level", `PLENO` for "Associate"; any other or absent value yields
  `UNKNOWN`.
- [x] The system shall set `matchedSkills` to the dictionary skills detected in the posting's
  title + description that are in `knownSkills`, and `missingSkills` to the detected skills that
  are not, each sorted alphabetically with no duplicates.
- [x] The system shall detect a dictionary skill when its `name` or any alias appears as a whole
  word (bounded by start/end of text or a non-alphanumeric character other than `.`, `+`, `#`),
  case-insensitively unless `caseSensitive: true`, so that `Java` does not match `JavaScript`
  and `C#`, `C++`, `.NET`, `Node.js` are detected intact.
- [x] The system shall set `category` to `MATCH` when `missingSkills` is empty, `STUDYABLE` when
  it has 1–3 entries, `STRETCH` when it has more than 3, and `UNRATED` when the description is
  not available.
- [x] The system shall order `jobs` by category (`MATCH`, `STUDYABLE`, `STRETCH`, `UNRATED`), then
  by `missingSkills` count ascending, then by `postedAt` descending (null last), then by `id`.
- [x] If a detail request fails or its page has no description element, then the system shall keep
  the posting with `descriptionAvailable: false`, empty skill lists, `category: "UNRATED"`,
  `seniority` from the title only, and set `"partial": true`.
- [x] If at least one search fails and at least one succeeds, then the system shall continue with
  the successful results and set `"partial": true`.
- [x] If every search fails, then the system shall respond `502` with
  `{"error": "<reason incl. upstream status or timeout>"}`.
- [x] When every search returns zero cards (or all are dropped as senior), the system shall
  respond `200` with `{"count": 0, "partial": false, "jobs": []}` and fetch no details.
- [x] If an upstream request fails, then the system shall log one WARN line naming the request
  (search keywords + location, or detail job id) and the status or exception type.

### Study plan endpoint
- [x] The system shall expose `GET /api/jobs/{id}/study-plan` returning `application/json`.
- [x] If `{id}` is not 1–20 decimal digits, then the system shall respond `400` with
  `{"error": ...}` and shall call neither LinkedIn nor the CLI.
- [x] When a valid request arrives, the system shall fetch that posting's detail page, compute
  `matchedSkills`/`missingSkills`/`seniority` with the same rules as the recommendations
  endpoint, and invoke the Claude CLI once.
- [x] The system shall pass the CLI a prompt (via stdin, not as a command-line argument)
  containing: the posting title, company and description; the profile's `knownSkills`; the
  computed `missingSkills`; `weeklyHours`; `maxWeeks`; and the instruction to return a
  Markdown, week-by-week plan in Brazilian Portuguese that closes the missing skills within
  `maxWeeks` weeks at `weeklyHours` hours per week, with a hands-on project per skill and, when
  the posting is written in English, English-for-interview practice.
- [x] The system shall respond `200` with
  `{"jobId", "title", "company", "url", "seniority", "matchedSkills", "missingSkills",
  "weeklyHours", "maxWeeks", "plan"}`, where `plan` is the CLI's result text, trimmed.
- [x] If LinkedIn answers `404` for the detail page, then the system shall respond `404` with
  `{"error": ...}` and shall not invoke the CLI.
- [x] If the detail request fails otherwise, or the page has no description, then the system shall
  respond `502` with `{"error": ...}` and shall not invoke the CLI.
- [x] If the CLI command cannot be started, exits non-zero, returns empty output, or exceeds the
  configured timeout (default 180 s), then the system shall kill the process if still running and
  respond `503` with `{"error": "<which of those happened>"}`, logging one WARN line.
- [x] The system shall read the CLI command (default `claude`), its fixed arguments, and timeout
  from `application.yml`, so tests can substitute a fake command.

## Constraints
- Same stack and rules as `specs/linkedin-guest-job-search.md`: Java 21, Spring Boot 3, Maven
  wrapper, Jsoup, `RestClient`, guest endpoints only, no cookies/credentials, no block-evasion.
- Upstream request budget per recommendations call: at most `searches × locations` (≤ 24) search
  requests plus `maxResults` (≤ 50) detail requests — ≤ 74, run sequentially with the inter-page
  delay. The existing `GET /api/jobs/search` behavior is unchanged.
- No persistence and no caching — every call fetches live and every study-plan call invokes the
  CLI.
- The CLI runs in non-interactive print mode with tool use disabled (verify the exact flags with
  `claude --help` for the installed version), because job descriptions are untrusted input and
  could carry prompt injection. Only the prompt goes through stdin; no shell interpolation of
  posting data.
- Runs on the engineer's Windows machine: resolve the CLI command so `claude` works there (e.g.
  `claude.cmd` via configuration), and the tests' fake command must run on Windows and on POSIX.
- `profile.yml` must not contain personal contact data; the résumé PDF is never copied into the
  repo.

## Out of scope
- Parsing the PDF at runtime or uploading a résumé.
- Sending results to Telegram (next spec), scheduled/automatic runs, and remembering seen jobs.
- Caching study plans or detail pages.
- Using the Anthropic API directly, or an LLM to choose searches/tracks.
- Detecting the posting's language beyond what the CLI infers from the description.
- Job sources other than LinkedIn; auth on our endpoints; Docker/deployment.

## Decisions & assumptions
- Deliverable → a feature in this service (not a one-off research document).
- Profile input → hand-curated `profile.yml` seeded from the PDF, skills/preferences only.
- "Going beyond the résumé" → adjacent tracks listed in `profile.yml`; seeded with `BACKEND_JVM`,
  `PLATFORM_DEVOPS`, `APPSEC_DEVSECOPS` (leverages the Cyber Defense degree), `AI_ENGINEER`,
  `MOBILE_REACT_NATIVE`.
- Fetch job descriptions → yes, via the public `jobPosting/{id}` guest endpoint; this lifts the
  previous spec's "no detail pages" exclusion for the new endpoints only.
- Study plans → generated by the engineer's local Claude Code CLI (the service runs on the same
  machine while it is on), not the API.
- "Can study for it" → `STUDYABLE` = 1–3 missing skills; senior-and-above postings are dropped.
- Search scope → remote Brazil + Grande SP on-site/hybrid + remote international (English).
- Output → JSON endpoints only; Telegram is the next spec.
- (Recommended default, not overridden) Study budget 10 h/week, plans of at most 8 weeks —
  stored in `profile.yml`, so changing it needs no code change.
- (Confirmed by the engineer) Study plans are on demand per job (`/api/jobs/{id}/study-plan`) instead of
  inline in the recommendations response, because each CLI call takes tens of seconds and the
  recommendations call already makes up to 70 rate-limited upstream requests.
- (Confirmed by the engineer) `UNKNOWN` seniority stays eligible for `MATCH`/`STUDYABLE`, because
  LinkedIn's "Mid-Senior level" lumps Pleno and Senior together; titles marked senior are already
  dropped.
- (Confirmed by the engineer) Recommendations default `postedWithin=WEEK`, `maxResults` default 30 / cap
  50; only the first page per search; ≤ 24 search pairs (see below); CLI timeout 180 s; CLI failures → `503`.
- (Implementation) The minimum seeded profile (7 searches × 3 locations = 21 pairs) exceeded the
  original 20-pair cap → engineer raised the cap to 24; worst case 24 searches + 50 details = 74
  upstream requests per recommendations call.
- (Implementation, engineer's decision) The live run showed the first searches filling
  `maxResults` on their own, so adjacent tracks never appeared → details are selected round-robin
  across tracks instead of in plain merge order.
- (Implementation) The fake CLI in tests is a Java class (`FakeClaude`, run with the test JVM)
  instead of a shell script, so the same tests run on Windows and POSIX.
- (Implementation) Skill boundaries: a trailing `.` ends a term unless a letter or digit follows
  it, so "Java." at the end of a sentence matches while "Node.jsx" and "ASP.NET" (for `.NET`) do not.
- (Implementation) International location seeded as `Latin America` (remote): the remote roles
  open to Brazilian residents usually list it.
- (Implementation) CLI args: `-p --output-format text --tools "" --strict-mcp-config
  --no-session-persistence` (verified with `claude --help`, Claude Code 2.1.283). On Windows a
  bare `claude` is resolved on the PATH to `claude.exe`/`claude.cmd`.
- (Implementation) Live check 2026-10-07: recommendations returned 8 real postings in ~43 s;
  study plan for job 4473418508 returned a Portuguese Markdown plan from the real CLI in ~48 s.
- (Review follow-up) Upstream WARN lines name the search's keywords/location, but `502` bodies name
  only `start=N` / `jobId=N`, so caller input is never echoed (matches the existing
  `typeMismatch` policy). Dropped the `HCL` (Terraform) and `embedding` aliases as false positives.
