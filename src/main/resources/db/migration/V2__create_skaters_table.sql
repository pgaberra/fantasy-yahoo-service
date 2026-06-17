-- Player read model (cached from Yahoo): identity + eligible positions + season stat line.
-- Refreshed by the sync job; served to the BFF. The id is Yahoo's player id.
CREATE TABLE skaters (
    id                     BIGINT       PRIMARY KEY,
    first_name             VARCHAR(100) NOT NULL,
    last_name              VARCHAR(100) NOT NULL,
    position               VARCHAR(4)   NOT NULL,   -- primary position: C, LW, RW, D
    sweater_number         INT,
    team_abbrev            VARCHAR(10),
    headshot               TEXT,
    games_played           INT,
    goals                  INT,
    assists                INT,
    points                 INT,
    plus_minus             INT,
    pim                    INT,
    power_play_goals       INT,
    power_play_points      INT,
    shorthanded_goals      INT,
    shorthanded_points     INT,
    game_winning_goals     INT,
    shots                  INT,
    shooting_pctg          DOUBLE PRECISION,
    avg_toi                VARCHAR(8),
    faceoff_winning_pctg   DOUBLE PRECISION,
    hits                   INT,
    blocked_shots          INT,
    total_faceoff_wins     INT,
    total_faceoff_losses   INT,
    -- Yahoo eligible positions, comma-joined (e.g. "C,LW").
    yahoo_positions        VARCHAR(50),
    synced_at              TIMESTAMPTZ  NOT NULL
);
