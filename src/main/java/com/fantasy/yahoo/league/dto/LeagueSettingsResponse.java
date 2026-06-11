package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LeagueSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String scoringType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StatCategory> statCategories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RosterSlot> rosterPositions
) {
}
