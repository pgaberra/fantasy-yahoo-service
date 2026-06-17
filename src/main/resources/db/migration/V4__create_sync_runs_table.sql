-- One row per sync run: outcome (counts) and the diff vs the previous run (players
-- added/removed). Powers the admin panel's sync status + diff view.
CREATE TABLE sync_runs (
    id               BIGSERIAL    PRIMARY KEY,
    started_at       TIMESTAMPTZ  NOT NULL,
    finished_at      TIMESTAMPTZ,
    status           VARCHAR(20)  NOT NULL,   -- success | failed
    skaters          INT,
    goalies          INT,
    added_count      INT,
    removed_count    INT,
    added            TEXT,                     -- JSON array of "Name (TEAM)"
    removed          TEXT,                     -- JSON array of "Name (TEAM)"
    error            TEXT
);

CREATE INDEX ix_sync_runs_started_at ON sync_runs (started_at DESC);
