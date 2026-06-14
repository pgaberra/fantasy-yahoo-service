-- Per-user Yahoo OAuth tokens, keyed by the fantasy app's user id (the JWT subject
-- the BFF forwards). Access and refresh tokens are stored AES-GCM encrypted, so the
-- columns hold base64 ciphertext, not the raw tokens.
CREATE TABLE yahoo_oauth_tokens (
    app_user_id        VARCHAR(64)  PRIMARY KEY,   -- fantasy app user id
    yahoo_guid         VARCHAR(64),                -- Yahoo's user GUID
    access_token_enc   VARCHAR(2048) NOT NULL,     -- encrypted access token
    refresh_token_enc  VARCHAR(2048) NOT NULL,     -- encrypted refresh token
    access_expires_at  TIMESTAMPTZ  NOT NULL,      -- when the access token expires
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
