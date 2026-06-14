package com.fantasy.yahoo.oauth;

/** Raised when an operation needs Yahoo tokens for a user who hasn't connected their account. */
public class YahooNotConnectedException extends RuntimeException {
    public YahooNotConnectedException(String message) {
        super(message);
    }
}
