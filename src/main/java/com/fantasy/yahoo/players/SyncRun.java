package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per sync run: outcome (counts) and the diff vs the previous run (players
 * added/removed, stored as JSON text). Field access for brevity.
 */
@Entity
@Table(name = "sync_runs")
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Instant startedAt;
    public Instant finishedAt;
    public String status;
    public Integer skaters;
    public Integer goalies;
    public Integer addedCount;
    public Integer removedCount;
    public String added;
    public String removed;
    public String error;
}
