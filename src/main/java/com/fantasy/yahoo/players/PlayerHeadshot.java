package com.fantasy.yahoo.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A player's headshot scaled down to a thumbnail. Yahoo's source images are full-resolution
 * cutouts — several megapixels and well over a megabyte each — for an avatar the UI draws at
 * 28px, so the sync stores a small square rendering here and the app never links the source.
 * Kept out of the skater/goalie tables so the list queries stay free of the image bytes.
 */
@Entity
@Table(name = "player_headshots")
public class PlayerHeadshot {

    @Id
    public Long playerId;
    public String sourceUrl;
    public byte[] image;
    public Instant updatedAt;
}
