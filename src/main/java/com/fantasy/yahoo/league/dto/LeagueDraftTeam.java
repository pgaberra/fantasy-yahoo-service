package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A team in a league's draft. */
public record LeagueDraftTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "465.l.12345.t.3") String teamKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for the authenticated user's own team.") boolean mine
) {
}
