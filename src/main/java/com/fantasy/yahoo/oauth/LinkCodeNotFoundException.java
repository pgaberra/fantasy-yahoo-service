package com.fantasy.yahoo.oauth;

/** The link code is unknown, already used or expired. */
public class LinkCodeNotFoundException extends RuntimeException {
    public LinkCodeNotFoundException(String message) {
        super(message);
    }
}
