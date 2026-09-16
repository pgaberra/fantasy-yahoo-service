package com.fantasy.yahoo.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tokens from a finished Yahoo consent, parked until the user who started the flow claims them.
 * Keyed by a hash of the one-time link code the callback hands to the browser, so the row alone
 * cannot be used to claim anything.
 */
@Entity
@Table(name = "yahoo_oauth_pending_links")
public class PendingYahooLink {

    @Id
    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private String appUserId;

    @Column(name = "yahoo_guid")
    private String yahooGuid;

    @Column(name = "access_token_enc", nullable = false, length = 2048)
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc", length = 2048)
    private String refreshTokenEnc;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PendingYahooLink() {
    }

    public PendingYahooLink(String codeHash, String appUserId, String yahooGuid, String accessTokenEnc,
                            String refreshTokenEnc, Instant accessExpiresAt, Instant expiresAt,
                            Instant createdAt) {
        this.codeHash = codeHash;
        this.appUserId = appUserId;
        this.yahooGuid = yahooGuid;
        this.accessTokenEnc = accessTokenEnc;
        this.refreshTokenEnc = refreshTokenEnc;
        this.accessExpiresAt = accessExpiresAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getYahooGuid() {
        return yahooGuid;
    }

    public String getAccessTokenEnc() {
        return accessTokenEnc;
    }

    public String getRefreshTokenEnc() {
        return refreshTokenEnc;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
