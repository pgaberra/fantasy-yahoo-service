-- Goalie read model (cached from Yahoo): identity + eligible positions + season stat line.
-- Refreshed by the sync job; served to the BFF. The id is Yahoo's player id.
CREATE TABLE goalies (
    id                     BIGINT       PRIMARY KEY,
    first_name             VARCHAR(100) NOT NULL,
    last_name              VARCHAR(100) NOT NULL,
    position               VARCHAR(4)   NOT NULL,   -- G
    sweater_number         INT,
    team_abbrev            VARCHAR(10),
    headshot               TEXT,
    games_played           INT,
    games_started          INT,
    wins                   INT,
    losses                 INT,
    shutouts               INT,
    shots_against          INT,
    saves                  INT,
    goals_against          INT,
    goals_against_avg      DOUBLE PRECISION,
    save_pctg              DOUBLE PRECISION,
    -- Yahoo eligible positions, comma-joined (e.g. "G").
    yahoo_positions        VARCHAR(50),
    synced_at              TIMESTAMPTZ  NOT NULL
);
