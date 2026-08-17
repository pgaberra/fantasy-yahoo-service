package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one live call to Yahoo actually did. The point is that nothing here throws: a 403 is an
 * answer, not a failure, and the whole reason this exists is to see the answer.
 */
public record YahooProbeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether Yahoo returned a usable page of players.")
        boolean ok,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The path asked for, so the game key and season filter are visible.")
        String path,

        @Schema(description = "Yahoo's HTTP status. Absent when the call never got a response — "
                + "no service-account token, a connection failure, or unparseable JSON.")
        Integer status,

        @Schema(description = "How many players the page carried. Yahoo pages 25 at a time, so 25 "
                + "means there is more behind it; 0 with ok=true means the collection is empty.")
        Integer players,

        @Schema(description = "Yahoo's own error text, or ours if the call never reached them. "
                + "Absent when the call succeeded.")
        String error
) {}
