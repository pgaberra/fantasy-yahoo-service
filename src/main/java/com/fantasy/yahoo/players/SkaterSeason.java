package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One skater's stat line for one season, cached from Yahoo. Kept apart from {@link Skater}, which
 * describes the player as they are now: the pool turns over between seasons while a finished
 * season's numbers never change again, so the two have different lifetimes.
 *
 * <p>{@code season} is the start year — 2025 is the 2025-26 season.
 */
@Entity
@Table(name = "skater_seasons")
@IdClass(PlayerSeasonId.class)
public class SkaterSeason {

    @Id
    public Long playerId;
    @Id
    public Integer season;

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
    public Instant syncedAt;
}
