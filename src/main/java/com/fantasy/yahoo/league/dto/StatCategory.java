package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A scoring stat category in the league. {@code pointValue} is the per-stat modifier
 * for points leagues (absent for category/roto leagues).
 */
public record StatCategory(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int statId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String displayName,
        String positionType,
        Double pointValue
) {
}
