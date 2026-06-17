package com.fantasy.yahoo.players.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record SyncRunResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
        Instant finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        Integer skaters,
        Integer goalies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int addedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int removedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> added,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> removed,
        String error
) {
}
