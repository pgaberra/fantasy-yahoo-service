package com.fantasy.yahoo.oauth;

/**
 * Raised when Yahoo refuses a stored grant outright — its {@code invalid_grant} answer, meaning the
 * refresh token was revoked, expired, or was issued to different client credentials.
 *
 * <p>Separate from a transient token-endpoint failure because the remedy is different: no retry can
 * revive a rejected grant, only fresh user consent. Callers should therefore throw the stored token
 * away rather than keep presenting it.
 */
public class YahooGrantRejectedException extends RuntimeException {
    public YahooGrantRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
