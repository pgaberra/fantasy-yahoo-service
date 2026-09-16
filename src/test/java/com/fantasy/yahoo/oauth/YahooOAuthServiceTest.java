package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import com.fantasy.yahoo.exception.YahooUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YahooOAuthServiceTest {

    private static final YahooOAuthProperties PROPS = new YahooOAuthProperties(
            "client-id", "client-secret", "https://yahoo.example/callback", "fspt-r",
            "state-signing-secret", "enc-key", "https://web.example/connect",
            "https://api.login.yahoo.example", "https://fantasy.example");

    private OAuthStateCodec stateCodec;
    private YahooTokenClient tokenClient;
    private TokenCipher cipher;
    private YahooOAuthTokenRepository tokenRepository;
    private PendingOAuthStateRepository pendingStateRepository;
    private YahooOAuthService service;

    @BeforeEach
    void setUp() {
        stateCodec = new OAuthStateCodec(PROPS);
        tokenClient = mock(YahooTokenClient.class);
        cipher = mock(TokenCipher.class);
        tokenRepository = mock(YahooOAuthTokenRepository.class);
        pendingStateRepository = mock(PendingOAuthStateRepository.class);
        service = new YahooOAuthService(PROPS, stateCodec, tokenClient, cipher,
                tokenRepository, pendingStateRepository);
    }

    @Test
    void buildAuthorizeUrl_persistsPendingState_andEmbedsMatchingSignedState() {
        String url = service.buildAuthorizeUrl("user-1");

        ArgumentCaptor<PendingOAuthState> captor = ArgumentCaptor.forClass(PendingOAuthState.class);
        verify(pendingStateRepository).save(captor.capture());
        PendingOAuthState saved = captor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo("user-1");
        assertThat(saved.getNonce()).isNotBlank();

        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        OAuthStateCodec.VerifiedState decoded = stateCodec.decodeAndVerify(state, Instant.now());
        assertThat(decoded.appUserId()).isEqualTo("user-1");
        assertThat(decoded.nonce()).isEqualTo(saved.getNonce());
    }

    @Test
    void handleCallback_consumesPendingState_exchangesCode_andStoresTokens() {
        Instant now = Instant.now();
        String nonce = "nonce-1";
        String state = stateCodec.encode("user-1", nonce, now);
        PendingOAuthState pending = new PendingOAuthState(nonce, "user-1", now.plusSeconds(600), now);
        when(pendingStateRepository.findById(nonce)).thenReturn(Optional.of(pending));
        when(tokenClient.exchangeCode("the-code"))
                .thenReturn(new YahooTokenClient.TokenResponse("at", "rt", 3600, "bearer", "guid"));
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.empty());
        when(cipher.encrypt(any())).thenReturn("ciphertext");

        service.handleCallback("the-code", state);

        verify(pendingStateRepository).delete(pending);
        verify(tokenClient).exchangeCode("the-code");
        verify(tokenRepository).save(any(YahooOAuthToken.class));
    }

    @Test
    void handleCallback_withUnknownOrAlreadyUsedState_isRejected_andExchangesNothing() {
        String state = stateCodec.encode("user-1", "nonce-x", Instant.now());
        when(pendingStateRepository.findById("nonce-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleCallback("the-code", state))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenClient, never()).exchangeCode(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void handleCallback_whenPendingUserDiffersFromState_isRejected_andExchangesNothing() {
        Instant now = Instant.now();
        String state = stateCodec.encode("user-1", "nonce-1", now);
        when(pendingStateRepository.findById("nonce-1"))
                .thenReturn(Optional.of(new PendingOAuthState("nonce-1", "user-2", now.plusSeconds(600), now)));

        assertThatThrownBy(() -> service.handleCallback("the-code", state))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenClient, never()).exchangeCode(any());
    }

    @Test
    void validAccessToken_whenYahooRejectsTheGrant_dropsTheTokenAndReportsNotConnected() {
        // The point of the drop: isConnected() must stop claiming a connection that cannot work,
        // so the UI offers a reconnect instead of retrying a token Yahoo will never honour.
        YahooOAuthToken stored = expiredToken();
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(stored));
        when(cipher.decrypt("rt-enc")).thenReturn("rt");
        when(tokenClient.refresh("rt")).thenThrow(
                new YahooGrantRejectedException("Yahoo rejected the grant", new RuntimeException()));

        assertThatThrownBy(() -> service.validAccessToken("user-1"))
                .isInstanceOf(YahooNotConnectedException.class);

        verify(tokenRepository).delete(stored);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void validAccessToken_whenTheTokenEndpointMerelyFails_keepsTheTokenForALaterRetry() {
        // The counterpart that stops the fix from over-reaching: a transient upstream failure
        // must not cost the user their connection.
        YahooOAuthToken stored = expiredToken();
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(stored));
        when(cipher.decrypt("rt-enc")).thenReturn("rt");
        when(tokenClient.refresh("rt")).thenThrow(new YahooUpstreamException("Yahoo token request failed"));

        assertThatThrownBy(() -> service.validAccessToken("user-1"))
                .isInstanceOf(YahooUpstreamException.class);

        verify(tokenRepository, never()).delete(any(YahooOAuthToken.class));
    }

    @Test
    void validAccessToken_whenNoAccountIsConnected_reportsNotConnectedAndDeletesNothing() {
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validAccessToken("user-1"))
                .isInstanceOf(YahooNotConnectedException.class);

        verify(tokenClient, never()).refresh(any());
        verify(tokenRepository, never()).delete(any(YahooOAuthToken.class));
    }

    @Test
    void validAccessToken_whenTheStoredAccessTokenCannotBeDecrypted_reportsNotConnectedAndKeepsTheRow() {
        YahooOAuthToken stored = expiredToken();
        stored.setAccessExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(stored));
        when(cipher.decrypt("at-enc")).thenThrow(
                new UnreadableTokenException("Failed to decrypt token", new RuntimeException()));

        assertThatThrownBy(() -> service.validAccessToken("user-1"))
                .isInstanceOf(YahooNotConnectedException.class);

        verify(tokenRepository, never()).delete(any(YahooOAuthToken.class));
    }

    @Test
    void validAccessToken_whenTheStoredRefreshTokenCannotBeDecrypted_reportsNotConnectedWithoutCallingYahoo() {
        YahooOAuthToken stored = expiredToken();
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(stored));
        when(cipher.decrypt("rt-enc")).thenThrow(
                new UnreadableTokenException("Failed to decrypt token", new RuntimeException()));

        assertThatThrownBy(() -> service.validAccessToken("user-1"))
                .isInstanceOf(YahooNotConnectedException.class);

        verify(tokenClient, never()).refresh(any());
        verify(tokenRepository, never()).delete(any(YahooOAuthToken.class));
    }

    /** A stored token whose access token has expired, so using it forces a refresh. */
    private static YahooOAuthToken expiredToken() {
        YahooOAuthToken token = new YahooOAuthToken();
        token.setAppUserId("user-1");
        token.setAccessTokenEnc("at-enc");
        token.setRefreshTokenEnc("rt-enc");
        token.setAccessExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return token;
    }
}
