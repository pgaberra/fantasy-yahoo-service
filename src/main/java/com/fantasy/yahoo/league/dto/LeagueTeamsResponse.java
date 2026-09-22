package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The teams in a user's Yahoo league, in draft order once Yahoo has set it, otherwise in Yahoo's team order. */
public record LeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueTeam> teams
) {
}
