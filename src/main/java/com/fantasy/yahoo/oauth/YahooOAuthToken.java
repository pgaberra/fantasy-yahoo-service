package com.fantasy.yahoo.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A user's Yahoo OAuth tokens, keyed by the fantasy app's user id (the JWT subject the
 * BFF forwards). Access and refresh tokens are stored encrypted (see {@link TokenCipher}).
 */
@Entity
@Table(name = "yahoo_oauth_tokens")
public class YahooOAuthToken {

    @Id
    @Column(name = "app_user_id", nullable = false, updatable = false)
    private String appUserId;

    @Column(name = "yahoo_guid")
    private String yahooGuid;

    @Column(name = "access_token_enc", nullable = false, length = 2048)
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc", nullable = false, length = 2048)
    private String refreshTokenEnc;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public YahooOAuthToken() {
    }

    public String getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(String appUserId) {
        this.appUserId = appUserId;
    }

    public String getYahooGuid() {
        return yahooGuid;
    }

    public void setYahooGuid(String yahooGuid) {
        this.yahooGuid = yahooGuid;
    }

    public String getAccessTokenEnc() {
        return accessTokenEnc;
    }

    public void setAccessTokenEnc(String accessTokenEnc) {
        this.accessTokenEnc = accessTokenEnc;
    }

    public String getRefreshTokenEnc() {
        return refreshTokenEnc;
    }

    public void setRefreshTokenEnc(String refreshTokenEnc) {
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(Instant accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
