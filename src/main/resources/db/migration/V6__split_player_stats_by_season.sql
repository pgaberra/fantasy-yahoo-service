-- One stat line per player *and season*, instead of one per player.
--
-- The read model held a single, unlabelled stat line, so caching a new season meant writing
-- over the previous one. That is wrong in both directions: the season we collect (the one being
-- played) and the season we show as a projection's reference (the one that finished) are
-- deliberately different for most of the year. Identity stays on skaters/goalies, which describe
-- the pool as it is now; the numbers move here, where they can say which season they belong to.
--
-- `season` is the start year: 2025 is the 2025-26 season.

CREATE TABLE skater_seasons (
    player_id              BIGINT       NOT NULL REFERENCES skaters (id) ON DELETE CASCADE,
    season                 INT          NOT NULL,
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
    synced_at              TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (player_id, season)
);

CREATE TABLE goalie_seasons (
    player_id              BIGINT       NOT NULL REFERENCES goalies (id) ON DELETE CASCADE,
    season                 INT          NOT NULL,
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
    synced_at              TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (player_id, season)
);

CREATE INDEX idx_skater_seasons_season ON skater_seasons (season);
CREATE INDEX idx_goalie_seasons_season ON goalie_seasons (season);

-- Carry the numbers we hold across as 2025-26, which is what they are: the sync has been frozen
-- since that season ended. This matters more than it looks — the 2025-26 game may no longer
-- serve those lines now that Yahoo has moved on, so a rebuild-from-scratch could not recover
-- them. On a fresh database both selects find nothing and the tables simply start empty.
INSERT INTO skater_seasons (
    player_id, season, games_played, goals, assists, points, plus_minus, pim,
    power_play_goals, power_play_points, shorthanded_goals, shorthanded_points,
    game_winning_goals, shots, shooting_pctg, avg_toi, faceoff_winning_pctg,
    hits, blocked_shots, total_faceoff_wins, total_faceoff_losses, synced_at)
SELECT id, 2025, games_played, goals, assists, points, plus_minus, pim,
       power_play_goals, power_play_points, shorthanded_goals, shorthanded_points,
       game_winning_goals, shots, shooting_pctg, avg_toi, faceoff_winning_pctg,
       hits, blocked_shots, total_faceoff_wins, total_faceoff_losses, synced_at
FROM skaters;

INSERT INTO goalie_seasons (
    player_id, season, games_played, games_started, wins, losses, shutouts,
    shots_against, saves, goals_against, goals_against_avg, save_pctg, synced_at)
SELECT id, 2025, games_played, games_started, wins, losses, shutouts,
       shots_against, saves, goals_against, goals_against_avg, save_pctg, synced_at
FROM goalies;

ALTER TABLE skaters
    DROP COLUMN games_played,
    DROP COLUMN goals,
    DROP COLUMN assists,
    DROP COLUMN points,
    DROP COLUMN plus_minus,
    DROP COLUMN pim,
    DROP COLUMN power_play_goals,
    DROP COLUMN power_play_points,
    DROP COLUMN shorthanded_goals,
    DROP COLUMN shorthanded_points,
    DROP COLUMN game_winning_goals,
    DROP COLUMN shots,
    DROP COLUMN shooting_pctg,
    DROP COLUMN avg_toi,
    DROP COLUMN faceoff_winning_pctg,
    DROP COLUMN hits,
    DROP COLUMN blocked_shots,
    DROP COLUMN total_faceoff_wins,
    DROP COLUMN total_faceoff_losses;

ALTER TABLE goalies
    DROP COLUMN games_played,
    DROP COLUMN games_started,
    DROP COLUMN wins,
    DROP COLUMN losses,
    DROP COLUMN shutouts,
    DROP COLUMN shots_against,
    DROP COLUMN saves,
    DROP COLUMN goals_against,
    DROP COLUMN goals_against_avg,
    DROP COLUMN save_pctg;
