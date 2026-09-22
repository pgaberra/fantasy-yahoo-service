package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One of a league's resources exactly as Yahoo sent it, or why it could not be read. */
public record YahooLeagueProbeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether Yahoo answered 200.")
        boolean ok,

        @Schema(description = "The path asked for. Absent when no call was made.")
        String path,

        @Schema(description = "Yahoo's HTTP status. Absent when the call never got a response.")
        Integer status,

        @Schema(description = "Yahoo's response body, verbatim. Absent when the call failed.")
        String body,

        @Schema(description = "Yahoo's own error text, or ours if the call never reached them.")
        String error
) {}
