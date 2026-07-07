package com.fantasy.yahoo.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String OAUTH_CALLBACK_PATH = "/api/v1/yahoo/oauth/callback";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${internal.api-key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    // Fail closed: this service is internet-facing and holds per-user Yahoo OAuth tokens, so it
    // must never serve an unauthenticated internal API. If no key is configured, refuse to start
    // rather than silently allowing all callers. Run any environment (local included) with
    // INTERNAL_API_KEY set; tests provide one.
    @PostConstruct
    void requireApiKey() {
        if (!StringUtils.hasText(expectedApiKey)) {
            throw new IllegalStateException(
                    "internal.api-key (INTERNAL_API_KEY) must be set. Refusing to start with an "
                    + "unauthenticated internal API.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isExempt(request);
    }

    // Decide the exemption from the decoded + normalised path (not the raw URI) and pin it to the
    // actuator health/info probes and the Yahoo OAuth callback only, so a traversal such as
    // /actuator/health/../../api/v1/yahoo/leagues cannot slip a protected path past the filter
    // under an exempt prefix. The callback is hit by Yahoo's browser redirect, which can't send
    // the internal key — it is secured by the signed `state` parameter instead.
    private static boolean isExempt(HttpServletRequest request) {
        String path;
        try {
            path = StringUtils.cleanPath(UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformedEncoding) {
            // A malformed %-sequence isn't an exempt path — fall through to the key check (401),
            // not exempt (and never an unhandled 500).
            return false;
        }
        // Never exempt a path still carrying a traversal or matrix-parameter segment (e.g.
        // /actuator/health/..;/../api/...) — those are classic normalisation-mismatch bypasses.
        if (path.contains("..") || path.contains(";")) {
            return false;
        }
        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info")
                || path.equals(OAUTH_CALLBACK_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!constantTimeEquals(expectedApiKey, providedKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid "
                    + API_KEY_HEADER + " header\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}
