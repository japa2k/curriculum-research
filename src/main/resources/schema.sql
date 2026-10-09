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
