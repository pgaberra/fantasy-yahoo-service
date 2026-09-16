-- Tokens from a completed Yahoo consent that are not yet attached to anyone. The callback cannot
-- tell whose browser it is running in, so it parks the tokens here under a one-time link code and
-- sends that code to the web app; they reach yahoo_oauth_tokens only when the signed-in user who
-- started the flow presents the code through the BFF. A code presented by anyone else discards the
-- row. Only a SHA-256 hash of the code is stored, and rows past expires_at are purged.
CREATE TABLE yahoo_oauth_pending_links (
    code_hash          VARCHAR(64)   PRIMARY KEY,   -- hex SHA-256 of the one-time link code
    app_user_id        VARCHAR(64)   NOT NULL,      -- the app user who started the flow
    yahoo_guid         VARCHAR(64),                 -- Yahoo's user GUID, when Yahoo sent one
    access_token_enc   VARCHAR(2048) NOT NULL,      -- encrypted access token
    refresh_token_enc  VARCHAR(2048),               -- encrypted refresh token, when Yahoo sent one
    access_expires_at  TIMESTAMPTZ   NOT NULL,
    expires_at         TIMESTAMPTZ   NOT NULL,      -- when the link code stops being accepted
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_yahoo_oauth_pending_links_expires_at
    ON yahoo_oauth_pending_links (expires_at);
