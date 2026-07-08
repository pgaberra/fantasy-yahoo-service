package com.fantasy.yahoo.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A pending Yahoo OAuth authorization this service issued but has not yet seen completed.
 * Keyed by a random {@code nonce} that is also embedded in the signed {@code state}; the
 * callback consumes (deletes) the matching row, which makes the state single-use and proves
 * the callback corresponds to a flow this service actually started.
 */
@Entity
@Table(name = "yahoo_oauth_pending_states")
public class PendingOAuthState {

    @Id
    @Column(name = "nonce", nullable = false, updatable = false)
    private String nonce;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private String appUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PendingOAuthState() {
    }

    public PendingOAuthState(String nonce, String appUserId, Instant expiresAt, Instant createdAt) {
        this.nonce = nonce;
        this.appUserId = appUserId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getNonce() {
        return nonce;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
