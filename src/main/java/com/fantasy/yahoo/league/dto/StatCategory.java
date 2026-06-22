package com.fantasy.yahoo.league.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A scoring stat category in the league. {@code pointValue} is the per-stat modifier
 * for points leagues (absent for category/roto leagues). {@code displayOnly} marks a stat
 * Yahoo shows but does not score (a component of a composite category, e.g. SA/SV/GA
 * feeding SV%/GAA).
 */
public record StatCategory(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int statId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String displayName,
        String positionType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when Yahoo marks the stat display-only (shown but not scored).")
        boolean displayOnly,
        Double pointValue
) {
}
