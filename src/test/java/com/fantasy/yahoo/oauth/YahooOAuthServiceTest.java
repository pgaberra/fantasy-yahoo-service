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
    private PendingYahooLinkRepository pendingLinkRepository;
    private YahooOAuthService service;
    private String linkCode;

    @BeforeEach
    void setUp() {
        stateCodec = new OAuthStateCodec(PROPS);
        tokenClient = mock(YahooTokenClient.class);
        cipher = mock(TokenCipher.class);
        tokenRepository = mock(YahooOAuthTokenRepository.class);
        pendingStateRepository = mock(PendingOAuthStateRepository.class);
        pendingLinkRepository = mock(PendingYahooLinkRepository.class);
        service = new YahooOAuthService(PROPS, stateCodec, tokenClient, cipher,
                tokenRepository, pendingStateRepository, pendingLinkRepository);
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

    /**
     * The callback runs in whichever browser finished consent, so it must not attach anything:
     * the tokens are parked until the user who started the flow claims them.
     */
    @Test
    void handleCallback_consumesPendingState_exchangesCode_andParksTokensWithoutAttachingThem() {
        Instant now = Instant.now();
        String nonce = "nonce-1";
        String state = stateCodec.encode("user-1", nonce, now);
        PendingOAuthState pending = new PendingOAuthState(nonce, "user-1", now.plusSeconds(600), now);
        when(pendingStateRepository.findById(nonce)).thenReturn(Optional.of(pending));
        when(tokenClient.exchangeCode("the-code"))
                .thenReturn(new YahooTokenClient.TokenResponse("at", "rt", 3600, "bearer", "guid"));
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.empty());
        when(cipher.encrypt(any())).thenReturn("ciphertext");

        YahooOAuthService.PendingLink link = service.handleCallback("the-code", state);

        verify(pendingStateRepository).delete(pending);
        verify(tokenClient).exchangeCode("the-code");
        verify(tokenRepository, never()).save(any());
        ArgumentCaptor<PendingYahooLink> captor = ArgumentCaptor.forClass(PendingYahooLink.class);
        verify(pendingLinkRepository).save(captor.capture());
        PendingYahooLink parked = captor.getValue();
        assertThat(parked.getAppUserId()).isEqualTo("user-1");
        assertThat(parked.getAccessTokenEnc()).isEqualTo("ciphertext");
        assertThat(parked.getYahooGuid()).isEqualTo("guid");
        assertThat(link.serviceAccount()).isFalse();
        assertThat(link.linkCode()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(parked.getCodeHash()).isNotEqualTo(link.linkCode()).hasSize(64);
    }

    @Test
    void handleCallback_forTheServiceAccount_saysSo() {
        Instant now = Instant.now();
        String state = stateCodec.encode(YahooOAuthService.SERVICE_ACCOUNT_ID, "nonce-s", now);
        when(pendingStateRepository.findById("nonce-s")).thenReturn(Optional.of(
                new PendingOAuthState("nonce-s", YahooOAuthService.SERVICE_ACCOUNT_ID, now.plusSeconds(600), now)));
        when(tokenClient.exchangeCode("the-code"))
                .thenReturn(new YahooTokenClient.TokenResponse("at", "rt", 3600, "bearer", "guid"));
        when(cipher.encrypt(any())).thenReturn("ciphertext");

        assertThat(service.handleCallback("the-code", state).serviceAccount()).isTrue();
    }

    @Test
    void completeLink_byTheUserWhoStartedTheFlow_attachesTheParkedTokens_andSpendsTheCode() {
        PendingYahooLink parked = parkedLinkFor("user-1", Instant.now().plusSeconds(300));
        when(pendingLinkRepository.findById(parked.getCodeHash())).thenReturn(Optional.of(parked));
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.empty());

        service.completeLink("user-1", linkCode);

        verify(pendingLinkRepository).delete(parked);
        ArgumentCaptor<YahooOAuthToken> captor = ArgumentCaptor.forClass(YahooOAuthToken.class);
        verify(tokenRepository).save(captor.capture());
        YahooOAuthToken saved = captor.getValue();
        assertThat(saved.getAppUserId()).isEqualTo("user-1");
        assertThat(saved.getAccessTokenEnc()).isEqualTo("at-enc");
        assertThat(saved.getRefreshTokenEnc()).isEqualTo("rt-enc");
        assertThat(saved.getYahooGuid()).isEqualTo("guid");
    }

    /**
     * The attack this step exists for: someone starts a connect, sends their consent link to a
     * victim, and the victim approves it. The victim's browser then claims the code as the victim,
     * which is not who started it, so the victim's Yahoo tokens are thrown away rather than stored
     * under the sender's account.
     */
    @Test
    void completeLink_byAnyoneElse_isRejected_andDiscardsTheTokens() {
        PendingYahooLink parked = parkedLinkFor("attacker", Instant.now().plusSeconds(300));
        when(pendingLinkRepository.findById(parked.getCodeHash())).thenReturn(Optional.of(parked));

        assertThatThrownBy(() -> service.completeLink("victim", linkCode))
                .isInstanceOf(LinkCodeUserMismatchException.class);

        verify(pendingLinkRepository).delete(parked);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void completeLink_withAnUnknownOrUsedCode_isRejected() {
        when(pendingLinkRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeLink("user-1", "never-issued"))
                .isInstanceOf(LinkCodeNotFoundException.class);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void completeLink_afterTheCodeExpired_isRejected_andDiscardsTheTokens() {
        PendingYahooLink parked = parkedLinkFor("user-1", Instant.now().minusSeconds(1));
        when(pendingLinkRepository.findById(parked.getCodeHash())).thenReturn(Optional.of(parked));

        assertThatThrownBy(() -> service.completeLink("user-1", linkCode))
                .isInstanceOf(LinkCodeNotFoundException.class);

        verify(pendingLinkRepository).delete(parked);
        verify(tokenRepository, never()).save(any());
    }

    /** Parks tokens exactly as the callback would, and returns the row it saved. */
    private PendingYahooLink parkedLinkFor(String appUserId, Instant expiresAt) {
        Instant now = Instant.now();
        String state = stateCodec.encode(appUserId, "nonce-p", now);
        when(pendingStateRepository.findById("nonce-p"))
                .thenReturn(Optional.of(new PendingOAuthState("nonce-p", appUserId, now.plusSeconds(600), now)));
        when(tokenClient.exchangeCode("the-code"))
                .thenReturn(new YahooTokenClient.TokenResponse("at", "rt", 3600, "bearer", "guid"));
        when(cipher.encrypt("at")).thenReturn("at-enc");
        when(cipher.encrypt("rt")).thenReturn("rt-enc");
        linkCode = service.handleCallback("the-code", state).linkCode();
        ArgumentCaptor<PendingYahooLink> captor = ArgumentCaptor.forClass(PendingYahooLink.class);
        verify(pendingLinkRepository).save(captor.capture());
        PendingYahooLink saved = captor.getValue();
        return new PendingYahooLink(saved.getCodeHash(), saved.getAppUserId(), saved.getYahooGuid(),
                saved.getAccessTokenEnc(), saved.getRefreshTokenEnc(), saved.getAccessExpiresAt(),
                expiresAt, saved.getCreatedAt());
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

    @Test
    void isConnected_withAReadableStoredToken_isTrue() {
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(expiredToken()));
        when(cipher.decrypt("rt-enc")).thenReturn("rt");

        assertThat(service.isConnected("user-1")).isTrue();
    }

    @Test
    void isConnected_withNoStoredToken_isFalse() {
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.empty());

        assertThat(service.isConnected("user-1")).isFalse();
    }

    @Test
    void isConnected_whenTheStoredTokenCannotBeDecrypted_isFalse_andKeepsTheRow() {
        // After a key change the row still exists but no use of it can work; reporting it as
        // connected hid the reconnect that is the only remedy.
        when(tokenRepository.findByAppUserId("user-1")).thenReturn(Optional.of(expiredToken()));
        when(cipher.decrypt("rt-enc")).thenThrow(
                new UnreadableTokenException("Failed to decrypt token", new RuntimeException()));

        assertThat(service.isConnected("user-1")).isFalse();
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
