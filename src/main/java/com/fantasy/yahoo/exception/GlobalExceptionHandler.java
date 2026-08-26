package com.fantasy.yahoo.exception;

import com.fantasy.yahoo.oauth.YahooNotConnectedException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(YahooNotConnectedException.class)
    public ResponseEntity<ErrorDto> handleNotConnected(YahooNotConnectedException e) {
        // Expected client outcome (user hasn't connected Yahoo), not a server fault.
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleBadRequest(IllegalArgumentException e) {
        // Expected client outcome (e.g. invalid OAuth state), not a server fault.
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException e) {
        // Bean Validation on a request parameter of a @Validated controller: the caller sent
        // something out of range. An expected client outcome, so no stack trace.
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDto> handleUpstreamFailure(IllegalStateException e) {
        // Raised when the upstream Yahoo API call fails — a real failure, so log it.
        log.error("Upstream Yahoo API call failed", e);
        return build(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception e) {
        // An unmatched exception must never be silently swallowed — log the full trace.
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorDto> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorDto.of(status.value(), status.getReasonPhrase(), message));
    }
}
