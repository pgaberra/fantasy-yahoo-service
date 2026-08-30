package com.fantasy.yahoo.players;

/**
 * Which source image a stored thumbnail was made from and how it was rendered, read without
 * loading its bytes.
 */
public record HeadshotSource(Long playerId, String sourceUrl, String recipe) {
}
