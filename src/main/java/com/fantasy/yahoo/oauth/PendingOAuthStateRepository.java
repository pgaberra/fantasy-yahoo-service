package com.fantasy.yahoo.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface PendingOAuthStateRepository extends JpaRepository<PendingOAuthState, String> {

    /** Removes states whose TTL has passed; returns the number deleted. */
    long deleteByExpiresAtBefore(Instant cutoff);
}
