package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A roster position slot, e.g. {@code C × 2}, {@code BN × 4}. */
public record RosterSlot(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int count,
        String positionType
) {
}
