package com.fantasy.yahoo.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface PendingYahooLinkRepository extends JpaRepository<PendingYahooLink, String> {

    long deleteByExpiresAtBefore(Instant cutoff);
}
