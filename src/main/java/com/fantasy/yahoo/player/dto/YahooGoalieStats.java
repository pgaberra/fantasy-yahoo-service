package com.fantasy.yahoo.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A goalie's season stat line parsed from Yahoo. All fields are nullable: a goalie with no
 * recorded stats for the season (or a stat Yahoo renders as "-") yields null.
 */
public record YahooGoalieStats(
        Integer gamesPlayed,
        Integer gamesStarted,
        Integer wins,
        Integer losses,
        Integer shutouts,
        Integer shotsAgainst,
        Integer saves,
        Integer goalsAgainst,
        @Schema(description = "Goals against average")
        Double goalsAgainstAvg,
        @Schema(description = "Save percentage as a fraction (e.g. 0.911)")
        Double savePct) {
}
