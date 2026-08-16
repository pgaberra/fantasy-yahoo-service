CREATE TABLE player_headshots (
    player_id  BIGINT PRIMARY KEY,
    source_url TEXT        NOT NULL,
    image      BYTEA       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
