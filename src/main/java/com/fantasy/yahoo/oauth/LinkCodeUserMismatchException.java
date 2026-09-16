package com.fantasy.yahoo.oauth;

/**
 * The link code belongs to a Yahoo connect flow another app user started. The pending tokens are
 * discarded before this is thrown: it is the shape of someone else's consent link being completed
 * in this user's browser.
 */
public class LinkCodeUserMismatchException extends RuntimeException {
    public LinkCodeUserMismatchException(String message) {
        super(message);
    }
}
