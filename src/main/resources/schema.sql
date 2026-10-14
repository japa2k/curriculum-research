CREATE TABLE IF NOT EXISTS jobs (
    url      TEXT PRIMARY KEY,
    title    TEXT NOT NULL,
    company  TEXT NOT NULL,
    location TEXT NOT NULL,
    found_at TEXT NOT NULL,
    -- Null until the job is posted in the Telegram chat.
    sent_at  TEXT
);

-- The full posting text, read from LinkedIn only when the job is about to be sent.
CREATE TABLE IF NOT EXISTS job_descriptions (
    url         TEXT PRIMARY KEY REFERENCES jobs (url),
    description TEXT NOT NULL,
    fetched_at  TEXT NOT NULL
);

-- Jobs that will never be sent: applied to off LinkedIn (no Easy Apply), or closed.
CREATE TABLE IF NOT EXISTS skipped_jobs (
    url        TEXT PRIMARY KEY REFERENCES jobs (url),
    reason     TEXT NOT NULL,
    skipped_at TEXT NOT NULL
);
