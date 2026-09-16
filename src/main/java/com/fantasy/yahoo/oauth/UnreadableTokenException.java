package com.fantasy.yahoo.oauth;

/**
 * A stored token that the configured key cannot decrypt. With AES-GCM that almost always means the
 * row was encrypted under a different {@code TOKEN_ENCRYPTION_KEY} (the key was rotated, or the row
 * came from another environment), not that the value is corrupt. Only fresh consent replaces it.
 *
 * Extends {@link IllegalStateException} so callers that caught the cipher's failures before keep
 * doing so.
 */
public class UnreadableTokenException extends IllegalStateException {
    public UnreadableTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
