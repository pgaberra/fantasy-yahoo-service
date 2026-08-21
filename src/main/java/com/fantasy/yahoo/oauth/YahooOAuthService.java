package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(YahooOAuthService.class);

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

    /**
     * Whether a usable Yahoo connection exists. True means a stored token Yahoo has not refused —
     * {@link #validAccessToken} drops the row as soon as it does, so this cannot go on claiming a
     * connection that no longer works.
     */
    public boolean isConnected(String appUserId) {
        return repository.existsByAppUserId(appUserId);
    }

    /**
     * A valid Yahoo access token for the user, refreshed if necessary.
     *
     * <p>When Yahoo rejects the stored refresh token the row is deleted rather than kept and
     * retried. A rejected grant is permanent — only fresh consent replaces it — so keeping it would
     * leave {@link #isConnected} reporting a connection that cannot work, hide the reconnect the
     * user actually needs, and log an upstream ERROR on every retry. The caller is told the account
     * is not connected, which by then is exactly true.
     *
     * <p>{@code noRollbackFor} is what makes the deletion stick: it has to survive the exception
     * that reports it. Safe because this method is the outermost transaction on every path that
     * reaches it — no caller of it is itself {@code @Transactional}.
     */
    @Transactional(noRollbackFor = YahooNotConnectedException.class)
    public String validAccessToken(String appUserId) {
        YahooOAuthToken token = repository.findByAppUserId(appUserId)
                .orElseThrow(() -> new YahooNotConnectedException(
                        "No Yahoo account is connected for this user"));
        Instant now = Instant.now();
        if (now.isBefore(token.getAccessExpiresAt().minusSeconds(EXPIRY_SKEW_SECONDS))) {
            return cipher.decrypt(token.getAccessTokenEnc());
        }
        YahooTokenClient.TokenResponse refreshed;
        try {
            refreshed = tokenClient.refresh(cipher.decrypt(token.getRefreshTokenEnc()));
        } catch (YahooGrantRejectedException e) {
            repository.delete(token);
            // WARN, not ERROR: the service did its job and the remedy is a human reconnecting an
            // account, so this must not raise a Sentry alert every time something retries. The id
            // is reduced to a constant because appUserId is request-supplied.
            log.warn("Yahoo rejected the stored refresh token for the {}; dropped it, "
                    + "reconnect required", SERVICE_ACCOUNT_ID.equals(appUserId) ? "service account" : "user");
            throw new YahooNotConnectedException(
                    "Yahoo rejected the stored authorization; reconnect the Yahoo account");
        }
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
