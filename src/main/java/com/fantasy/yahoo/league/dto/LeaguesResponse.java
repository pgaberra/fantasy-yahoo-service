package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LeaguesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueSummary> leagues
) {
}
