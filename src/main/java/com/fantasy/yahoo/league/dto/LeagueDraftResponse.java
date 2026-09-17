package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A Yahoo league's draft: its status, its teams and every pick Yahoo lists. */
public record LeagueDraftResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for an auction draft, whose picks are bought rather than taken in turn.")
        boolean auction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The league's teams in first-round draft order once Yahoo has set it, "
                        + "otherwise in Yahoo's team order.")
        List<LeagueDraftTeam> teams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every pick Yahoo lists, by overall pick number. Before the draft this can hold "
                        + "the order's slots without players, or nothing at all.")
        List<LeagueDraftPick> picks
) {
}
