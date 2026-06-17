package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cached skater read model: identity, Yahoo eligible positions and the season stat line,
 * refreshed from Yahoo by the sync job. Field access (public fields) keeps this wide,
 * boilerplate-free entity readable; Hibernate maps camelCase fields to snake_case columns.
 */
@Entity
@Table(name = "skaters")
public class Skater {

    @Id
    public Long id;
    public String firstName;
    public String lastName;
    public String position;
    public Integer sweaterNumber;
    public String teamAbbrev;
    public String headshot;
    public Integer gamesPlayed;
    public Integer goals;
    public Integer assists;
    public Integer points;
    public Integer plusMinus;
    public Integer pim;
    public Integer powerPlayGoals;
    public Integer powerPlayPoints;
    public Integer shorthandedGoals;
    public Integer shorthandedPoints;
    public Integer gameWinningGoals;
    public Integer shots;
    public Double shootingPctg;
    public String avgToi;
    public Double faceoffWinningPctg;
    public Integer hits;
    public Integer blockedShots;
    public Integer totalFaceoffWins;
    public Integer totalFaceoffLosses;
    public String yahooPositions;
    public Instant syncedAt;
}
