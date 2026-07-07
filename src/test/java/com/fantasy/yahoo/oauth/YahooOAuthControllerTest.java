package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class YahooOAuthControllerTest {

    private static final String WEB = "https://web.example.test/connect";

    private YahooOAuthService oauthService;
    private YahooOAuthController controller;

    @BeforeEach
    void setUp() {
        oauthService = mock(YahooOAuthService.class);
        YahooOAuthProperties props = new YahooOAuthProperties(
                "", "", "", "", "", "", WEB, "", "");
        controller = new YahooOAuthController(oauthService, props);
    }

    @Test
    void callback_withValidCodeAndState_connectsAndRedirects() {
        ResponseEntity<Void> response = controller.callback("the-code", "the-state", null);

        verify(oauthService).handleCallback("the-code", "the-state");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString(WEB + "?yahoo=connected");
    }

    @Test
    void callback_whenConsentDenied_redirectsWithErrorAndDoesNotCallService() {
        ResponseEntity<Void> response = controller.callback(null, "the-state", "access_denied");

        verify(oauthService, never()).handleCallback(any(), any());
        assertThat(response.getHeaders().getLocation()).hasToString(WEB + "?yahoo=error");
    }

    @Test
    void callback_withMissingCode_redirectsWithErrorAndDoesNotCallService() {
        ResponseEntity<Void> response = controller.callback(null, "the-state", null);

        verify(oauthService, never()).handleCallback(any(), any());
        assertThat(response.getHeaders().getLocation()).hasToString(WEB + "?yahoo=error");
    }

    @Test
    void callback_withInvalidState_redirectsWithError() {
        doThrow(new IllegalArgumentException("OAuth state signature mismatch"))
                .when(oauthService).handleCallback("the-code", "bad-state");

        ResponseEntity<Void> response = controller.callback("the-code", "bad-state", null);

        assertThat(response.getHeaders().getLocation()).hasToString(WEB + "?yahoo=error");
    }
}
