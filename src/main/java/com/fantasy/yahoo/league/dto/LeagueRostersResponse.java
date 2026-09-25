package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Every team in a Yahoo league with the players it holds now. */
public record LeagueRostersResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The league's teams in Yahoo's team order, each with its current roster.")
        List<LeagueRosterTeam> teams
) {
}
