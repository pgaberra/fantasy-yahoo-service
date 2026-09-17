package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One pick of a league's draft. */
public record LeagueDraftPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Overall pick number, from 1.") int pick,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int round,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String teamKey,
        @Schema(description = "Yahoo's player key; absent for a pick not yet made.", example = "465.p.6743")
        String playerKey,
        @Schema(description = "The player id inside the player key, the id the player read model uses; "
                + "absent for a pick not yet made.", example = "6743")
        Integer playerId
) {
}
