package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    // 32 zero bytes, base64-encoded — a valid AES-256 key for tests only.
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private TokenCipher cipher(String key) {
        return new TokenCipher(new YahooOAuthProperties(
                null, null, null, null, null, key, null, null, null));
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        TokenCipher cipher = cipher(KEY);
        String secret = "ya29.some-yahoo-access-token";

        String encrypted = cipher.encrypt(secret);

        assertThat(encrypted).isNotEqualTo(secret);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    void encrypt_usesRandomIv_soCiphertextDiffersEachTime() {
        TokenCipher cipher = cipher(KEY);

        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void encrypt_withoutKey_fails() {
        assertThatThrownBy(() -> cipher("").encrypt("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_underADifferentKey_isReportedAsUnreadable() {
        // What a rotated TOKEN_ENCRYPTION_KEY does to every row written before the rotation.
        String otherKey = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
        String encrypted = cipher(otherKey).encrypt("ya29.some-yahoo-access-token");

        assertThatThrownBy(() -> cipher(KEY).decrypt(encrypted))
                .isInstanceOf(UnreadableTokenException.class);
    }

    @Test
    void decrypt_withoutKey_isAConfigurationFault_notAnUnreadableToken() {
        assertThatThrownBy(() -> cipher("").decrypt("anything"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(UnreadableTokenException.class);
    }
}
