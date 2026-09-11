package com.fantasy.yahoo.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

/**
 * Configuration for the Yahoo OAuth 2.0 flow and the Fantasy Sports API.
 *
 * The four secrets (client id/secret, signing and encryption keys) come only from environment
 * variables, with no default, and are validated when the properties are bound: a missing or
 * malformed one stops the service at startup. Otherwise it deploys healthy and fails only when a
 * user connects Yahoo, in a way that reads as Yahoo refusing. Non-secret URLs and the scope
 * carry sensible defaults.
 */
@Validated
@ConfigurationProperties(prefix = "yahoo.oauth")
public record YahooOAuthProperties(
        @NotBlank(message = "YAHOO_CLIENT_ID must be set")
        String clientId,
        @NotBlank(message = "YAHOO_CLIENT_SECRET must be set")
        String clientSecret,
        // The callback URL registered with the Yahoo app. Must match exactly. Yahoo
        // rejects localhost and shared free-hosting domains (e.g. *.onrender.com), so
        // staging needs a custom domain pointed at this service.
        String redirectUri,
        // Yahoo Fantasy read scope.
        String scope,
        // HMAC key used to sign the short-lived OAuth `state` (carries the app user id).
        @NotBlank(message = "YAHOO_STATE_SECRET must be set")
        String stateSecret,
        // Base64-encoded 256-bit AES key used to encrypt stored access/refresh tokens.
        @NotBlank(message = "TOKEN_ENCRYPTION_KEY must be set")
        String tokenEncryptionKey,
        // Where the browser is redirected after a successful connect (the web app).
        String webPostConnectUrl,
        // Yahoo OAuth endpoints host (request_auth / get_token).
        String loginBaseUrl,
        // Yahoo Fantasy Sports API base.
        String apiBaseUrl
) {

    /** A blank key is reported by its own constraint; this one only judges a key that is there. */
    @AssertTrue(message = "TOKEN_ENCRYPTION_KEY must be base64 that decodes to 32 bytes (AES-256)")
    public boolean isTokenEncryptionKey256Bit() {
        if (!StringUtils.hasText(tokenEncryptionKey)) {
            return true;
        }
        try {
            return Base64.getDecoder().decode(tokenEncryptionKey).length == 32;
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }
}
