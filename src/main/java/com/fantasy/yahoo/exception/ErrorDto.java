package com.fantasy.yahoo.exception;

import java.time.Instant;

public record ErrorDto(
        Instant timestamp,
        int status,
        String error,
        String message
) {
    public static ErrorDto of(int status, String error, String message) {
        return new ErrorDto(Instant.now(), status, error, message);
    }
}
