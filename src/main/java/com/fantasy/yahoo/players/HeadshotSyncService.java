package com.fantasy.yahoo.players;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Keeps {@link PlayerHeadshot} in step with the player read model: downloads each new or
 * changed source image once, stores it as a thumbnail, and drops the rows of players who have
 * left. Only players whose source URL has changed are fetched again, so a routine sync costs
 * a handful of downloads rather than the whole league.
 */
@Service
public class HeadshotSyncService {

    private static final Logger log = LoggerFactory.getLogger(HeadshotSyncService.class);

    private static final long MAX_SOURCE_BYTES = 16L * 1024 * 1024;
    private static final int CONCURRENT_DOWNLOADS = 4;
    private static final int SAVE_BATCH = 100;

    private final PlayerHeadshotRepository headshotRepository;
    private final RestClient restClient;

    public HeadshotSyncService(PlayerHeadshotRepository headshotRepository,
                               @Qualifier("headshotRestClient") RestClient restClient) {
        this.headshotRepository = headshotRepository;
        this.restClient = restClient;
    }

    public HeadshotRefreshResult refresh(Map<Long, String> sourceUrlsByPlayerId) {
        Map<Long, String> stored = new HashMap<>();
        for (HeadshotSource source : headshotRepository.findAllSources()) {
            stored.put(source.playerId(), source.sourceUrl());
        }

        Map<Long, String> outdated = new HashMap<>();
        sourceUrlsByPlayerId.forEach((playerId, sourceUrl) -> {
            if (!sourceUrl.equals(stored.get(playerId))) {
                outdated.put(playerId, sourceUrl);
            }
        });

        Set<Long> departed = new HashSet<>(stored.keySet());
        departed.removeAll(sourceUrlsByPlayerId.keySet());
        headshotRepository.deleteAllById(departed);

        int refreshed = download(outdated);
        log.info("Headshot refresh: {} of {} players refreshed, {} failed, {} removed",
                refreshed, outdated.size(), outdated.size() - refreshed, departed.size());
        return new HeadshotRefreshResult(refreshed, outdated.size() - refreshed, departed.size());
    }

    private int download(Map<Long, String> sourceUrlsByPlayerId) {
        if (sourceUrlsByPlayerId.isEmpty()) {
            return 0;
        }
        List<Future<PlayerHeadshot>> pending = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_DOWNLOADS)) {
            sourceUrlsByPlayerId.forEach((playerId, sourceUrl) ->
                    pending.add(pool.submit(() -> thumbnail(playerId, sourceUrl))));
        }

        List<PlayerHeadshot> batch = new ArrayList<>(SAVE_BATCH);
        int saved = 0;
        for (Future<PlayerHeadshot> future : pending) {
            PlayerHeadshot headshot = resolve(future);
            if (headshot == null) {
                continue;
            }
            batch.add(headshot);
            saved++;
            if (batch.size() == SAVE_BATCH) {
                headshotRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            headshotRepository.saveAll(batch);
        }
        return saved;
    }

    private static PlayerHeadshot resolve(Future<PlayerHeadshot> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private PlayerHeadshot thumbnail(Long playerId, String sourceUrl) {
        try {
            PlayerHeadshot headshot = new PlayerHeadshot();
            headshot.playerId = playerId;
            headshot.sourceUrl = sourceUrl;
            headshot.image = HeadshotThumbnailer.toThumbnail(fetch(sourceUrl));
            headshot.updatedAt = Instant.now();
            return headshot;
        } catch (Exception e) {
            // One unreadable image is not worth failing a sync over; the player simply keeps
            // whatever thumbnail it had, or renders without one. The source URL is Yahoo's and
            // stays out of the log line — the player's row already holds it.
            log.warn("Could not build a headshot thumbnail for player {}: {}",
                    playerId, sanitizeForLog(e.toString()));
            return null;
        }
    }

    private static String sanitizeForLog(String message) {
        return message.replace('\r', ' ').replace('\n', ' ');
    }

    private byte[] fetch(String sourceUrl) {
        return restClient.get()
                .uri(URI.create(sourceUrl))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IOException("Headshot source responded " + response.getStatusCode());
                    }
                    long length = response.getHeaders().getContentLength();
                    if (length > MAX_SOURCE_BYTES) {
                        throw new IOException("Headshot source is " + length + " bytes");
                    }
                    return response.getBody().readNBytes((int) MAX_SOURCE_BYTES);
                });
    }
}
