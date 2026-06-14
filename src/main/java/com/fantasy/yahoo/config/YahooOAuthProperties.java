package com.fantasy.yahoo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Yahoo OAuth 2.0 flow and the Fantasy Sports API.
 *
 * Secrets (client id/secret, signing and encryption keys) come from environment
 * variables and default to empty so the app still boots for tests/CI — the OAuth
 * endpoints simply fail at call time when they are unset. Non-secret URLs and the
 * scope carry sensible defaults.
 */
@ConfigurationProperties(prefix = "yahoo.oauth")
public record YahooOAuthProperties(
        String clientId,
        String clientSecret,
        // The callback URL registered with the Yahoo app. Must match exactly. Yahoo
        // rejects localhost and shared free-hosting domains (e.g. *.onrender.com), so
        // staging needs a custom domain pointed at this service.
        String redirectUri,
        // Yahoo Fantasy read scope.
        String scope,
        // HMAC key used to sign the short-lived OAuth `state` (carries the app user id).
        String stateSecret,
        // Base64-encoded 256-bit AES key used to encrypt stored access/refresh tokens.
        String tokenEncryptionKey,
        // Where the browser is redirected after a successful connect (the web app).
        String webPostConnectUrl,
        // Yahoo OAuth endpoints host (request_auth / get_token).
        String loginBaseUrl,
        // Yahoo Fantasy Sports API base.
        String apiBaseUrl
) {
}
