package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthStateCodecTest {

    private OAuthStateCodec codec(String secret) {
        return new OAuthStateCodec(new YahooOAuthProperties(
                null, null, null, null, secret, null, null, null, null));
    }

    @Test
    void encodeThenDecode_returnsAppUserIdAndNonce() {
        OAuthStateCodec codec = codec("state-signing-secret");
        Instant now = Instant.ofEpochSecond(1_000_000);

        String state = codec.encode("user-42", "nonce-9", now);

        OAuthStateCodec.VerifiedState decoded = codec.decodeAndVerify(state, now);
        assertThat(decoded.appUserId()).isEqualTo("user-42");
        assertThat(decoded.nonce()).isEqualTo("nonce-9");
    }

    @Test
    void decode_withTamperedSignature_fails() {
        OAuthStateCodec codec = codec("state-signing-secret");
        Instant now = Instant.ofEpochSecond(1_000_000);
        String state = codec.encode("user-42", "nonce-9", now);
        String tampered = state.substring(0, state.length() - 2) + "xy";

        assertThatThrownBy(() -> codec.decodeAndVerify(tampered, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_withDifferentSecret_fails() {
        Instant now = Instant.ofEpochSecond(1_000_000);
        String state = codec("secret-a").encode("user-42", "nonce-9", now);

        assertThatThrownBy(() -> codec("secret-b").decodeAndVerify(state, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_whenExpired_fails() {
        OAuthStateCodec codec = codec("state-signing-secret");
        String state = codec.encode("user-42", "nonce-9", Instant.ofEpochSecond(0));

        assertThatThrownBy(() -> codec.decodeAndVerify(state, Instant.ofEpochSecond(100_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
