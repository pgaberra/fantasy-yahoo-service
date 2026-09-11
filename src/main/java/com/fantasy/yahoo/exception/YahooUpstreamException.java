package com.fantasy.yahoo.exception;

/**
 * Yahoo itself failed: its token endpoint or Fantasy API refused, errored, could not be reached,
 * or answered with something unreadable. Thrown only by the two classes that talk to Yahoo, and
 * the only exception the advice turns into a 502.
 *
 * <p>Its own type because a JDK exception carries no fixed meaning: an {@link IllegalStateException}
 * from a missing signing key or an undecryptable token is this service's fault, and blaming Yahoo
 * for it sends whoever reads the alert to the wrong place.
 */
public class YahooUpstreamException extends RuntimeException {

    public YahooUpstreamException(String message) {
        super(message);
    }

    public YahooUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
