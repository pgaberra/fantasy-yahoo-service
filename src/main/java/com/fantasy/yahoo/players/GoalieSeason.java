package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One goalie's stat line for one season, cached from Yahoo. See {@link SkaterSeason} for why the
 * numbers live apart from the identity.
 */
@Entity
@Table(name = "goalie_seasons")
@IdClass(PlayerSeasonId.class)
public class GoalieSeason {

    @Id
    public Long playerId;
    @Id
    public Integer season;

    public Integer gamesPlayed;
    public Integer gamesStarted;
    public Integer wins;
    public Integer losses;
    public Integer shutouts;
    public Integer shotsAgainst;
    public Integer saves;
    public Integer goalsAgainst;
    public Double goalsAgainstAvg;
    public Double savePctg;
    public Instant syncedAt;
}
