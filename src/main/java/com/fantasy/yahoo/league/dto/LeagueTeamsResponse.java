package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The teams in a user's Yahoo league, in Yahoo's team order. */
public record LeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueTeam> teams
) {
}
