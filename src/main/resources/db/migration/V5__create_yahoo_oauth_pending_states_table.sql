-- Single-use, server-issued OAuth `state` records. Each authorize-url call inserts one row
-- (keyed by a random nonce that is also embedded in the signed `state`); the callback looks the
-- nonce up and deletes it, so a given state can be completed at most once and only if this
-- service actually issued it. Rows past `expires_at` are purged periodically.
CREATE TABLE yahoo_oauth_pending_states (
    nonce        VARCHAR(64)  PRIMARY KEY,   -- random per-flow id, also carried in the signed state
    app_user_id  VARCHAR(64)  NOT NULL,      -- the app user the flow was started for
    expires_at   TIMESTAMPTZ  NOT NULL,      -- mirrors the signed state's TTL
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_yahoo_oauth_pending_states_expires_at
    ON yahoo_oauth_pending_states (expires_at);
