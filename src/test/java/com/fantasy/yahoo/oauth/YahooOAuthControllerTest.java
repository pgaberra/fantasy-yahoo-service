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
        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=declined&detail=access_denied");
    }

    /**
     * The difference that matters: someone pressing no looks nothing like Yahoo refusing to let
     * the app ask at all, and only Yahoo's own word for it tells them apart.
     */
    @Test
    void callback_whenYahooRefusesTheScope_passesItsOwnWordOn() {
        ResponseEntity<Void> response = controller.callback(null, "the-state", "invalid_scope");

        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=declined&detail=invalid_scope");
    }

    /** Only a known vocabulary reaches the redirect -- the value came from the request. */
    @Test
    void callback_withAnErrorWeDoNotKnow_dropsIt() {
        ResponseEntity<Void> response = controller.callback(null, "the-state", "../../evil");

        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=declined");
    }

    @Test
    void callback_withMissingCode_redirectsWithErrorAndDoesNotCallService() {
        ResponseEntity<Void> response = controller.callback(null, "the-state", null);

        verify(oauthService, never()).handleCallback(any(), any());
        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=declined");
    }

    /**
     * An expired state is the likely shape of a slow reconnect -- longer than the state's ten
     * minutes. Saying so is the difference between "try again, faster" and hunting a problem
     * that is not there.
     */
    @Test
    void callback_withInvalidState_redirectsWithError() {
        doThrow(new IllegalArgumentException("OAuth state signature mismatch"))
                .when(oauthService).handleCallback("the-code", "bad-state");

        ResponseEntity<Void> response = controller.callback("the-code", "bad-state", null);

        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=invalid_state");
    }

    /**
     * Yahoo refusing the code exchange is the one outcome that puts the fault on Yahoo rather
     * than on whoever pressed the button, so it must not be lumped in with the others.
     */
    @Test
    void callback_whenTheExchangeFails_saysSo() {
        doThrow(new IllegalStateException("Yahoo token request failed: 400 Bad Request"))
                .when(oauthService).handleCallback("the-code", "the-state");

        ResponseEntity<Void> response = controller.callback("the-code", "the-state", null);

        assertThat(response.getHeaders().getLocation())
                .hasToString(WEB + "?yahoo=error&reason=exchange_failed");
    }
}
