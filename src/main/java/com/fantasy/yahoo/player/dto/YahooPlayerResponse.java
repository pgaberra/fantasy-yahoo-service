package com.fantasy.yahoo.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record YahooPlayerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Yahoo's player id")
        String yahooId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Player full name")
        String fullName,
        @Schema(description = "Yahoo's editorial team abbreviation")
        String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Yahoo fantasy eligible positions (e.g. [\"C\",\"LW\"])")
        List<String> eligiblePositions) {
}
