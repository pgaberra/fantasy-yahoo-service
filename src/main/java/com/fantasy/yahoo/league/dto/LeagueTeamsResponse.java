package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The teams in a user's Yahoo league, in draft order once Yahoo has set it, otherwise in Yahoo's team order. */
public record LeagueTeamsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeagueTeam> teams,
        @Schema(description = "The signed-in manager's own seat in the draft order, counting from 1. "
                + "Null when Yahoo names it nowhere, which is not the same as the first seat: the teams "
                + "then carry Yahoo's own order, which says nothing about who picks when.")
        Integer draftPosition
) {
}
