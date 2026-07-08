package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns the per-user Yahoo OAuth 2.0 authorization-code flow and token lifecycle.
 *
 * connect: build the Yahoo consent URL with a signed `state` carrying the app user id and a
 *          single-use nonce recorded server-side.
 * callback: verify state, consume the nonce, exchange the code for tokens, store them encrypted.
 * access: hand out a valid access token, refreshing it transparently when expired.
 */
@Service
public class YahooOAuthService {

    /**
     * Reserved app-user id for the single, app-owned Yahoo "service account" whose token is
     * used for non-user-specific bulk fetches (e.g. the league-wide player list). Real users
     * are keyed by UUID, so this sentinel can never collide with one.
     */
    public static final String SERVICE_ACCOUNT_ID = "__service__";

    // Refresh a little before the token actually expires to avoid races near the boundary.
    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final YahooOAuthProperties props;
    private final OAuthStateCodec stateCodec;
    private final YahooTokenClient tokenClient;
    private final TokenCipher cipher;
    private final YahooOAuthTokenRepository repository;
    private final PendingOAuthStateRepository pendingStateRepository;

    public YahooOAuthService(YahooOAuthProperties props,
                             OAuthStateCodec stateCodec,
                             YahooTokenClient tokenClient,
                             TokenCipher cipher,
                             YahooOAuthTokenRepository repository,
                             PendingOAuthStateRepository pendingStateRepository) {
        this.props = props;
        this.stateCodec = stateCodec;
        this.tokenClient = tokenClient;
        this.cipher = cipher;
        this.repository = repository;
        this.pendingStateRepository = pendingStateRepository;
    }

    /** The Yahoo consent URL the browser should be sent to, for the given app user. */
    @Transactional
    public String buildAuthorizeUrl(String appUserId) {
        Instant now = Instant.now();
        String nonce = UUID.randomUUID().toString();
        pendingStateRepository.save(
                new PendingOAuthState(nonce, appUserId, now.plusSeconds(stateCodec.ttlSeconds()), now));
        String state = stateCodec.encode(appUserId, nonce, now);
        return UriComponentsBuilder.fromUriString(props.loginBaseUrl())
                .path("/oauth2/request_auth")
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", props.scope())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /** Handles the Yahoo callback: verifies + consumes the state, exchanges the code, stores tokens. */
    @Transactional
    public void handleCallback(String code, String state) {
        Instant now = Instant.now();
        OAuthStateCodec.VerifiedState verified = stateCodec.decodeAndVerify(state, now);
        // Single-use: the nonce must match a pending authorization this service issued. Consuming
        // it here means the same state can never be replayed, and a state we never issued (no
        // matching row) is rejected before any token exchange happens.
        PendingOAuthState pending = pendingStateRepository.findById(verified.nonce())
                .orElseThrow(() -> new IllegalArgumentException("Unknown or already-used OAuth state"));
        pendingStateRepository.delete(pending);
        if (!pending.getAppUserId().equals(verified.appUserId())) {
            throw new IllegalArgumentException("OAuth state does not match its pending authorization");
        }
        YahooTokenClient.TokenResponse tokens = tokenClient.exchangeCode(code);
        upsert(verified.appUserId(), tokens, now);
    }

    /** Drops pending states whose TTL has passed so the table stays small. */
    @Scheduled(cron = "${yahoo.oauth.pending-state-purge-cron:0 20 * * * *}")
    @Transactional
    public void purgeExpiredPendingStates() {
        pendingStateRepository.deleteByExpiresAtBefore(Instant.now());
    }

    public boolean isConnected(String appUserId) {
        return repository.existsByAppUserId(appUserId);
    }

    /** A valid Yahoo access token for the user, refreshed if necessary. (Used in phase 2.) */
    @Transactional
    public String validAccessToken(String appUserId) {
        YahooOAuthToken token = repository.findByAppUserId(appUserId)
                .orElseThrow(() -> new YahooNotConnectedException(
                        "No Yahoo account is connected for this user"));
        Instant now = Instant.now();
        if (now.isBefore(token.getAccessExpiresAt().minusSeconds(EXPIRY_SKEW_SECONDS))) {
            return cipher.decrypt(token.getAccessTokenEnc());
        }
        YahooTokenClient.TokenResponse refreshed =
                tokenClient.refresh(cipher.decrypt(token.getRefreshTokenEnc()));
        upsert(appUserId, refreshed, now);
        return refreshed.accessToken();
    }

    private void upsert(String appUserId, YahooTokenClient.TokenResponse tokens, Instant now) {
        YahooOAuthToken entity = repository.findByAppUserId(appUserId)
                .orElseGet(() -> {
                    YahooOAuthToken fresh = new YahooOAuthToken();
                    fresh.setAppUserId(appUserId);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        entity.setAccessTokenEnc(cipher.encrypt(tokens.accessToken()));
        // Yahoo rotates the refresh token only sometimes; keep the previous one if absent.
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            entity.setRefreshTokenEnc(cipher.encrypt(tokens.refreshToken()));
        }
        if (tokens.xoauthYahooGuid() != null) {
            entity.setYahooGuid(tokens.xoauthYahooGuid());
        }
        entity.setAccessExpiresAt(tokens.expiresAt(now));
        entity.setUpdatedAt(now);
        repository.save(entity);
    }
}
