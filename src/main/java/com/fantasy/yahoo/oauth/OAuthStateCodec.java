package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes the OAuth {@code state} parameter as a short-lived, HMAC-signed token that carries
 * the app user id plus a random per-flow nonce. The signature protects the callback against a
 * forged state; the nonce is also stored server-side (see {@link PendingOAuthState}) so the
 * callback can prove the state matches an authorization this service actually issued and can
 * consume it, making the state single-use.
 *
 * Format: {@code base64url(appUserId ":" nonce ":" expiryEpochSeconds) "." base64url(hmacSha256)}.
 */
@Component
public class OAuthStateCodec {

    private static final long TTL_SECONDS = 600; // 10 minutes
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final String secret;

    public OAuthStateCodec(YahooOAuthProperties props) {
        this.secret = props.stateSecret();
    }

    /** How long an issued state stays valid — also the lifetime of its server-side pending row. */
    public long ttlSeconds() {
        return TTL_SECONDS;
    }

    public String encode(String appUserId, String nonce, Instant now) {
        String payload = appUserId + ":" + nonce + ":" + (now.getEpochSecond() + TTL_SECONDS);
        String payloadB64 = ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + ENC.encodeToString(hmac(payloadB64));
    }

    /** The app user id and nonce, if the state is well-formed, unexpired and correctly signed. */
    public VerifiedState decodeAndVerify(String state, Instant now) {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("Missing OAuth state");
        }
        String[] parts = state.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }
        if (!constantTimeEquals(parts[1], ENC.encodeToString(hmac(parts[0])))) {
            throw new IllegalArgumentException("OAuth state signature mismatch");
        }
        String payload = new String(DEC.decode(parts[0]), StandardCharsets.UTF_8);
        int lastColon = payload.lastIndexOf(':');
        int prevColon = lastColon < 0 ? -1 : payload.lastIndexOf(':', lastColon - 1);
        if (prevColon < 0) {
            throw new IllegalArgumentException("Malformed OAuth state payload");
        }
        long expiry = Long.parseLong(payload.substring(lastColon + 1));
        if (now.getEpochSecond() > expiry) {
            throw new IllegalArgumentException("OAuth state expired");
        }
        String appUserId = payload.substring(0, prevColon);
        String nonce = payload.substring(prevColon + 1, lastColon);
        return new VerifiedState(appUserId, nonce);
    }

    public record VerifiedState(String appUserId, String nonce) {
    }

    private byte[] hmac(String data) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("YAHOO_STATE_SECRET is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OAuth state", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
