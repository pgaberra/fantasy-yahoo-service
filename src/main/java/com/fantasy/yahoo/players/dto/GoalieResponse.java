package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A goalie from the cached read model: identity, Yahoo eligible positions and the season
 * stat line. Bio fields are required; stat fields are nullable (no recorded stats).
 */
public record GoalieResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo fantasy eligible positions (e.g. [\"G\"])")
        List<String> eligiblePositions,
        Integer sweaterNumber,
        String teamAbbrev,
        @Schema(description = "Yahoo's source image URL, present only when a thumbnail is served "
                + "at /api/v1/players/{id}/headshot — clients should render that endpoint, not this URL")
        String headshot,
        Integer gamesPlayed,
        Integer gamesStarted,
        Integer wins,
        Integer losses,
        Integer shutouts,
        Integer shotsAgainst,
        Integer saves,
        Integer goalsAgainst,
        Double goalsAgainstAvg,
        Double savePctg
) {
}
