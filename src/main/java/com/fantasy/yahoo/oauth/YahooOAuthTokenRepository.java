package com.fantasy.yahoo.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YahooOAuthTokenRepository extends JpaRepository<YahooOAuthToken, String> {

    Optional<YahooOAuthToken> findByAppUserId(String appUserId);

    boolean existsByAppUserId(String appUserId);
}
