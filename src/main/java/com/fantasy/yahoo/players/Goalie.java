package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cached goalie read model: who the player is now. The numbers live in {@link GoalieSeason},
 * one row per season — see {@link Skater} for why.
 */
@Entity
@Table(name = "goalies")
public class Goalie {

    @Id
    public Long id;
    public String firstName;
    public String lastName;
    public String position;
    public Integer sweaterNumber;
    public String teamAbbrev;
    public String headshot;
    public String yahooPositions;
    public Instant syncedAt;
}
