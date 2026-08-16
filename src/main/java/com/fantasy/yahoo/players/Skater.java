package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cached skater read model: who the player is now — identity, team and Yahoo eligible
 * positions — refreshed from Yahoo by the sync job. The numbers live in {@link SkaterSeason},
 * one row per season, because the pool turns over while a finished season's stats do not.
 * Field access (public fields) keeps this entity readable; Hibernate maps camelCase fields to
 * snake_case columns.
 */
@Entity
@Table(name = "skaters")
public class Skater {

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
