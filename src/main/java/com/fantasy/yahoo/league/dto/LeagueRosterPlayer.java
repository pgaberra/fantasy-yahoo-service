package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A player on a team's current roster. */
public record LeagueRosterPlayer(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "465.p.6743") String playerKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The player id inside the player key, the id the player read model and the "
                        + "draft's picks use.", example = "6743")
        int playerId,
        @Schema(description = "The slot the manager has him in today (C, LW, D, G, Util, BN, IR, IR+, NA, …); "
                + "absent when Yahoo does not say.", example = "BN")
        String selectedPosition
) {
}
