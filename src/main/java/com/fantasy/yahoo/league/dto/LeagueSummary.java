package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A fantasy league the user belongs to, in NHL-native-ish Yahoo shape. */
public record LeagueSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        Integer season,
        Integer numTeams,
        String scoringType
) {
}
