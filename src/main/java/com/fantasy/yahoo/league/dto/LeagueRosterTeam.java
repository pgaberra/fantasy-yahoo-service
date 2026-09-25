package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A team in a league and the players on its roster today, after every trade, drop and pickup. */
public record LeagueRosterTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "465.l.12345.t.3") String teamKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the authenticated user's own team.") boolean mine,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The team's current roster in Yahoo's order, bench and injured reserve included; "
                        + "empty before the draft.")
        List<LeagueRosterPlayer> players
) {
}
