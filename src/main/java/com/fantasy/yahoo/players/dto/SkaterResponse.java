package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A skater from the cached read model: identity, Yahoo eligible positions and the season
 * stat line. Bio fields are required; stat fields are nullable (no recorded stats).
 */
public record SkaterResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo fantasy eligible positions (e.g. [\"C\",\"LW\"])")
        List<String> eligiblePositions,
        Integer sweaterNumber,
        String teamAbbrev,
        @Schema(description = "Yahoo's source image URL, present only when a thumbnail is served "
                + "at /api/v1/players/{id}/headshot — clients should render that endpoint, not this URL")
        String headshot,
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
        Integer shots,
        Double shootingPctg,
        String avgToi,
        Double faceoffWinningPctg,
        Integer hits,
        Integer blockedShots,
        Integer totalFaceoffWins,
        Integer totalFaceoffLosses
) {
}
