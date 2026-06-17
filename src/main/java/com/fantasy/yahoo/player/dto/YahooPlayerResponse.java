package com.fantasy.yahoo.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A Yahoo player with identity, headshot, eligible positions and a season stat line.
 * Exactly one of {@code skaterStats} / {@code goalieStats} is populated (per {@code goalie}).
 */
public record YahooPlayerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Yahoo's player id")
        String yahooId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Player full name")
        String fullName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Player first name")
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Player last name")
        String lastName,
        @Schema(description = "Yahoo's editorial team abbreviation")
        String teamAbbrev,
        @Schema(description = "Primary position (single, e.g. \"C\")")
        String position,
        @Schema(description = "Uniform / sweater number")
        Integer uniformNumber,
        @Schema(description = "Full-resolution headshot image URL")
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether the player is a goalie")
        boolean goalie,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo fantasy eligible positions (e.g. [\"C\",\"LW\"])")
        List<String> eligiblePositions,
        @Schema(description = "Season skater stat line; null for goalies")
        YahooSkaterStats skaterStats,
        @Schema(description = "Season goalie stat line; null for skaters")
        YahooGoalieStats goalieStats) {
}
