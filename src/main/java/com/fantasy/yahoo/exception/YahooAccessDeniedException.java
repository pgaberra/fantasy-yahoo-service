package com.fantasy.yahoo.exception;

/**
 * Yahoo answered 403: it will not let this application, or this account, do what was asked.
 * Carries Yahoo's own sentence ("This application is not authorized to perform this action.").
 *
 * <p>Not a {@link YahooUpstreamException}, because a refusal is not an outage. Sent as a 502 it
 * read as Yahoo being down, and the BFF replaced it with "a downstream service is unavailable",
 * so from 2026-08-26 until Yahoo restored the app's access every screen blamed us for a verdict
 * only Yahoo could change. Retrying does not help either: the answer holds until Yahoo grants it.
 */
public class YahooAccessDeniedException extends RuntimeException {

    public YahooAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
