package com.fantasy.yahoo.player.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A player no team in the league owns: a free agent, or one on waivers")
public record YahooAvailablePlayerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Yahoo's player id")
        String yahooId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "The NHL club Yahoo has him on, in Yahoo's own abbreviation")
        String teamAbbrev,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "His primary fantasy position")
        String position,
        Integer uniformNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean goalie,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every position he may be started at in this league")
        List<String> eligiblePositions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "FREE_AGENT for a player who can be added now, WAIVERS for one who "
                        + "has to clear waivers first, UNKNOWN when Yahoo does not say")
        YahooAvailability availability) {}
