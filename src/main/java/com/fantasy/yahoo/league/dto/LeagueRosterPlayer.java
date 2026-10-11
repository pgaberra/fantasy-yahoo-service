package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** A player on a team's current roster. */
public record LeagueRosterPlayer(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "465.p.6743") String playerKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The player id inside the player key, the id the player read model and the "
                        + "draft's picks use.", example = "6743")
        int playerId,
        @Schema(description = "The slot the manager has him in today (C, LW, D, G, Util, BN, IR, IR+, NA, …); "
                + "absent when Yahoo does not say.", example = "BN")
        String selectedPosition,
        @Schema(description = "His full name; absent when Yahoo does not say.", example = "Connor McDavid")
        String fullName,
        @Schema(description = "The NHL club Yahoo has him on, in Yahoo's own abbreviation.", example = "EDM")
        String teamAbbrev,
        Integer uniformNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True for a goalie (Yahoo's position type G).")
        boolean goalie,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position he may be started at in this league; empty when Yahoo does not say.")
        List<String> eligiblePositions,
        @Schema(description = "Yahoo's injury or availability status (DTD, O, IR, IR-LT, NA, …); absent for a "
                + "healthy player.", example = "DTD")
        String status
) {
}
