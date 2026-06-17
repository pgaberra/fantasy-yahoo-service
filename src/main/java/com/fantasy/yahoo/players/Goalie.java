package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cached goalie read model: identity, Yahoo eligible positions and the season stat line,
 * refreshed from Yahoo by the sync job. Field access (public fields) keeps this wide,
 * boilerplate-free entity readable; Hibernate maps camelCase fields to snake_case columns.
 */
@Entity
@Table(name = "goalies")
public class Goalie {

    @Id
    public Long id;
    public String firstName;
    public String lastName;
    public String position;
    public Integer sweaterNumber;
    public String teamAbbrev;
    public String headshot;
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
    public String yahooPositions;
    public Instant syncedAt;
}
