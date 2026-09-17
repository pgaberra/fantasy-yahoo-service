package com.fantasy.yahoo.player.dto;

/**
 * How a player who is not on a roster can be picked up. The distinction is the manager's whole
 * decision on a Tuesday night: a free agent is in the lineup tonight, a player on waivers is not.
 */
public enum YahooAvailability {
    FREE_AGENT,
    WAIVERS,
    UNKNOWN;

    /** Yahoo's own {@code ownership_type} wording. Anything else is UNKNOWN rather than a guess. */
    public static YahooAvailability of(String ownershipType) {
        if (ownershipType == null) {
            return UNKNOWN;
        }
        return switch (ownershipType) {
            case "freeagents" -> FREE_AGENT;
            case "waivers" -> WAIVERS;
            default -> UNKNOWN;
        };
    }
}
