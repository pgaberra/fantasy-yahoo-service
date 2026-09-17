package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Owns the per-user Yahoo OAuth 2.0 authorization-code flow and token lifecycle.
 *
 * connect: build the Yahoo consent URL with a signed `state` carrying the app user id and a
 *          single-use nonce recorded server-side.
 * callback: verify state, consume the nonce, exchange the code for tokens and park them, encrypted,
 *           under a one-time link code for the browser to carry back to the web app.
 * complete: attach the parked tokens, but only for the app user who started the flow. The state
 *           proves which user started it, not whose browser finished it, so without this step a
 *           consent link sent to someone else would store their Yahoo account under the sender's.
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

    private static final long LINK_CODE_TTL_SECONDS = 300;
    private static final int LINK_CODE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final YahooOAuthProperties props;
    private final OAuthStateCodec stateCodec;
    private final YahooTokenClient tokenClient;
    private final TokenCipher cipher;
    private final YahooOAuthTokenRepository repository;
    private final PendingOAuthStateRepository pendingStateRepository;
    private final PendingYahooLinkRepository pendingLinkRepository;

    public YahooOAuthService(YahooOAuthProperties props,
                             OAuthStateCodec stateCodec,
                             YahooTokenClient tokenClient,
                             TokenCipher cipher,
                             YahooOAuthTokenRepository repository,
                             PendingOAuthStateRepository pendingStateRepository,
                             PendingYahooLinkRepository pendingLinkRepository) {
        this.props = props;
        this.stateCodec = stateCodec;
        this.tokenClient = tokenClient;
        this.cipher = cipher;
        this.repository = repository;
        this.pendingStateRepository = pendingStateRepository;
        this.pendingLinkRepository = pendingLinkRepository;
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

    /** A parked Yahoo connection: the one-time code to hand to the browser, and who it is for. */
    public record PendingLink(String linkCode, boolean serviceAccount) {
    }

    /**
     * Handles the Yahoo callback: verifies + consumes the state, exchanges the code, and parks the
     * tokens under a one-time link code. Nothing is attached to an account here.
     */
    @Transactional
    public PendingLink handleCallback(String code, String state) {
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
        String linkCode = newLinkCode();
        String refresh = tokens.refreshToken() == null || tokens.refreshToken().isBlank()
                ? null : cipher.encrypt(tokens.refreshToken());
        pendingLinkRepository.save(new PendingYahooLink(
                hash(linkCode), verified.appUserId(), tokens.xoauthYahooGuid(),
                cipher.encrypt(tokens.accessToken()), refresh, tokens.expiresAt(now),
                now.plusSeconds(LINK_CODE_TTL_SECONDS), now));
        return new PendingLink(linkCode, SERVICE_ACCOUNT_ID.equals(verified.appUserId()));
    }

    /**
     * Attaches a parked connection to {@code appUserId}, if that is the user who started the flow.
     * The code is spent whatever the outcome, and a code presented by another user throws away the
     * tokens: that is someone else's consent link completed in this user's browser, and the tokens
     * belong to neither of them.
     */
    @Transactional(noRollbackFor = {LinkCodeNotFoundException.class, LinkCodeUserMismatchException.class})
    public void completeLink(String appUserId, String linkCode) {
        Instant now = Instant.now();
        PendingYahooLink pending = pendingLinkRepository.findById(hash(linkCode))
                .orElseThrow(() -> new LinkCodeNotFoundException("Unknown or already-used Yahoo link code"));
        pendingLinkRepository.delete(pending);
        pendingLinkRepository.flush();
        if (now.isAfter(pending.getExpiresAt())) {
            throw new LinkCodeNotFoundException("Yahoo link code expired");
        }
        if (!MessageDigest.isEqual(pending.getAppUserId().getBytes(StandardCharsets.UTF_8),
                appUserId.getBytes(StandardCharsets.UTF_8))) {
            throw new LinkCodeUserMismatchException(
                    "This Yahoo connection was started by a different account; it has been discarded");
        }
        YahooOAuthToken entity = repository.findByAppUserId(appUserId)
                .orElseGet(() -> {
                    YahooOAuthToken fresh = new YahooOAuthToken();
                    fresh.setAppUserId(appUserId);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        entity.setAccessTokenEnc(pending.getAccessTokenEnc());
        if (pending.getRefreshTokenEnc() != null) {
            entity.setRefreshTokenEnc(pending.getRefreshTokenEnc());
        }
        if (pending.getYahooGuid() != null) {
            entity.setYahooGuid(pending.getYahooGuid());
        }
        entity.setAccessExpiresAt(pending.getAccessExpiresAt());
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private static String newLinkCode() {
        byte[] bytes = new byte[LINK_CODE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String linkCode) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(linkCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Drops pending states and parked connections whose TTL has passed. */
    @Scheduled(cron = "${yahoo.oauth.pending-state-purge-cron:0 20 * * * *}")
    @Transactional
    public void purgeExpiredPendingStates() {
        Instant now = Instant.now();
        pendingStateRepository.deleteByExpiresAtBefore(now);
        pendingLinkRepository.deleteByExpiresAtBefore(now);
    }

    /**
     * Whether a usable Yahoo connection exists. True means a stored token Yahoo has not refused —
     * {@link #validAccessToken} drops the row as soon as it does — and that the configured key can
     * still read. An unreadable row is kept (see {@link #validAccessToken}), so existence alone
     * would report connected after a key change while every use of it fails, and the UI would never
     * offer the reconnect that fixes it. The refresh token is the one checked: every path through
     * {@link #validAccessToken} ends up needing it, and both tokens are written under the same key.
     *
     * <p>WARN, not ERROR: the status check runs on every page that shows the connection, and the
     * ERROR that has to reach Sentry for a wrong key is already raised where the token is used.
     */
    public boolean isConnected(String appUserId) {
        return repository.findByAppUserId(appUserId)
                .map(token -> {
                    try {
                        cipher.decrypt(token.getRefreshTokenEnc());
                        return true;
                    } catch (UnreadableTokenException e) {
                        log.warn("Stored Yahoo token cannot be decrypted with the configured "
                                + "TOKEN_ENCRYPTION_KEY; reporting not connected so a reconnect is offered");
                        return false;
                    }
                })
                .orElse(false);
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
     *
     * <p>A stored token the configured key cannot decrypt is also reported as not connected, since
     * a reconnect is the remedy there too, but the row is kept: if the key is only wrong for a
     * while, deleting would cost every user their connection.
     */
    @Transactional(noRollbackFor = YahooNotConnectedException.class)
    public String validAccessToken(String appUserId) {
        YahooOAuthToken token = repository.findByAppUserId(appUserId)
                .orElseThrow(() -> new YahooNotConnectedException(
                        "No Yahoo account is connected for this user"));
        Instant now = Instant.now();
        if (now.isBefore(token.getAccessExpiresAt().minusSeconds(EXPIRY_SKEW_SECONDS))) {
            return readable(SERVICE_ACCOUNT_ID.equals(appUserId), token.getAccessTokenEnc());
        }
        String refreshToken = readable(SERVICE_ACCOUNT_ID.equals(appUserId), token.getRefreshTokenEnc());
        YahooTokenClient.TokenResponse refreshed;
        try {
            refreshed = tokenClient.refresh(refreshToken);
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

    /** The id is reduced to service account or user before it gets here, as it is request-supplied. */
    private String readable(boolean serviceAccount, String stored) {
        try {
            return cipher.decrypt(stored);
        } catch (UnreadableTokenException e) {
            // ERROR, unlike a rejected grant: this is our own key and our own data disagreeing, and
            // if the key is misconfigured it hits every user, so it has to reach Sentry.
            // The subject is part of the literal, not a placeholder argument: FindSecBugs reads a
            // placeholder next to the exception as a CRLF injection.
            log.error(serviceAccount
                    ? "Stored Yahoo token for the service account cannot be decrypted with the configured "
                            + "TOKEN_ENCRYPTION_KEY (key changed, or the row was written under another key); "
                            + "kept it, reconnect required"
                    : "Stored Yahoo token for a user cannot be decrypted with the configured "
                            + "TOKEN_ENCRYPTION_KEY (key changed, or the row was written under another key); "
                            + "kept it, reconnect required", e);
            throw new YahooNotConnectedException(
                    "The stored Yahoo authorization can no longer be read; reconnect the Yahoo account");
        }
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
