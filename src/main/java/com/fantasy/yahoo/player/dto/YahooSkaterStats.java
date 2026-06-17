package com.fantasy.yahoo.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A skater's season stat line parsed from Yahoo. All fields are nullable: a player with no
 * recorded stats for the season (or a stat Yahoo renders as "-") yields null.
 */
public record YahooSkaterStats(
        Integer gamesPlayed,
        Integer goals,
        Integer assists,
        Integer points,
        Integer plusMinus,
        Integer pim,
        Integer powerPlayGoals,
        Integer powerPlayPoints,
        Integer shorthandedGoals,
        Integer shorthandedPoints,
        Integer gameWinningGoals,
        Integer shotsOnGoal,
        @Schema(description = "Shooting percentage as a fraction (e.g. 0.151)")
        Double shootingPct,
        Integer faceoffsWon,
        Integer faceoffsLost,
        Integer hits,
        Integer blocks,
        @Schema(description = "Average time on ice per game, formatted \"MM:SS\"")
        String avgTimeOnIce) {
}
