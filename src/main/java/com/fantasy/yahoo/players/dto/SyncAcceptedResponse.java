package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SyncAcceptedResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status
) {
}
