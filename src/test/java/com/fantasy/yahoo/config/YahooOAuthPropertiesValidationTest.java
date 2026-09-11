package com.fantasy.yahoo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A missing OAuth secret used to let the service deploy healthy and fail only when a user
 * connected Yahoo, in ways that read as Yahoo refusing. It now stops the service at startup.
 */
class YahooOAuthPropertiesValidationTest {

    private static final String VALID_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "yahoo.oauth.client-id=id",
                    "yahoo.oauth.client-secret=secret",
                    "yahoo.oauth.state-secret=state",
                    "yahoo.oauth.token-encryption-key=" + VALID_KEY);

    @Test
    void startsWithAllFourSecrets() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void refusesToStartWithABlankClientSecret() {
        runner.withPropertyValues("yahoo.oauth.client-secret=")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("YAHOO_CLIENT_SECRET must be set"));
    }

    @Test
    void refusesToStartWithoutAStateSecret() {
        runner.withPropertyValues("yahoo.oauth.state-secret= ")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("YAHOO_STATE_SECRET must be set"));
    }

    @Test
    void refusesToStartWithoutAClientIdOrEncryptionKey() {
        runner.withPropertyValues("yahoo.oauth.client-id=", "yahoo.oauth.token-encryption-key=")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("YAHOO_CLIENT_ID must be set")
                        .hasMessageContaining("TOKEN_ENCRYPTION_KEY must be set"));
    }

    /** The key is named in the failure but never printed: startup logs reach Sentry. */
    @Test
    void refusesAKeyThatIsNot256BitsWithoutEchoingIt() {
        String shortKey = "c2hvcnQta2V5LW9ubHktMTYtYg==";
        runner.withPropertyValues("yahoo.oauth.token-encryption-key=" + shortKey)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("TOKEN_ENCRYPTION_KEY must be base64 that decodes to 32 bytes")
                        .message().doesNotContain(shortKey));
    }

    @Test
    void refusesAKeyThatIsNotBase64() {
        runner.withPropertyValues("yahoo.oauth.token-encryption-key=not base64!")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("TOKEN_ENCRYPTION_KEY must be base64"));
    }

    @Configuration
    @EnableConfigurationProperties(YahooOAuthProperties.class)
    static class Config {
    }
}
