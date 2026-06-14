package com.fantasy.yahoo.config;

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

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${internal.api-key:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip the filter entirely when no key is configured (local dev / CI without env var).
        // Always pass through Actuator paths so health checks work regardless.
        // The Yahoo OAuth callback is hit by Yahoo's browser redirect, which can't send the
        // internal key — it is secured by the signed `state` parameter instead.
        return !StringUtils.hasText(expectedApiKey)
                || request.getRequestURI().startsWith("/actuator")
                || request.getRequestURI().equals("/api/v1/yahoo/oauth/callback");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!expectedApiKey.equals(providedKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid "
                    + API_KEY_HEADER + " header\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
