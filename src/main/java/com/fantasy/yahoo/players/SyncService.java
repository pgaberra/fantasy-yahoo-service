package com.fantasy.yahoo.players;

import com.fantasy.yahoo.player.YahooPlayerService;
import com.fantasy.yahoo.player.dto.YahooGoalieStats;
import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import com.fantasy.yahoo.player.dto.YahooSkaterStats;
import com.fantasy.yahoo.players.dto.SyncRunResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes the cached player read model from Yahoo. Pulls the full league-wide player list
 * straight from {@link YahooPlayerService} (no inter-service HTTP), splits skaters/goalies by
 * Yahoo's position type, upserts and deletes stale rows, and records each run + diff. If the
 * Yahoo fetch fails or returns nothing the run is recorded as failed and existing data is kept.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    // Own Jackson 2 mapper for the added/removed JSON columns: Spring Boot 4's bean is
    // Jackson 3, so we don't inject one (same pattern as YahooFantasyClient).
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final YahooPlayerService yahooPlayerService;
    private final SkaterRepository skaterRepository;
    private final GoalieRepository goalieRepository;
    private final SyncRunRepository syncRunRepository;
    private final String gameKey;
    private final String season;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncService(YahooPlayerService yahooPlayerService,
                       SkaterRepository skaterRepository,
                       GoalieRepository goalieRepository,
                       SyncRunRepository syncRunRepository,
                       @Value("${sync.yahoo-game-key:nhl}") String gameKey,
                       @Value("${sync.yahoo-season:}") String season) {
        this.yahooPlayerService = yahooPlayerService;
        this.skaterRepository = skaterRepository;
        this.goalieRepository = goalieRepository;
        this.syncRunRepository = syncRunRepository;
        this.gameKey = gameKey;
        this.season = season;
    }

    public boolean isRunning() {
        return running.get();
    }

    public SyncRun sync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A sync is already running");
        }
        Instant started = Instant.now();
        try {
            return runSync(started);
        } catch (Exception e) {
            log.error("Player sync failed", e);
            return syncRunRepository.save(failedRun(started, e));
        } finally {
            running.set(false);
        }
    }

    private SyncRun runSync(Instant started) {
        Map<Long, String> previousLabels = new HashMap<>();
        Set<Long> previousSkaterIds = new HashSet<>();
        Set<Long> previousGoalieIds = new HashSet<>();
        for (Skater s : skaterRepository.findAll()) {
            previousSkaterIds.add(s.id);
            previousLabels.put(s.id, label(s.firstName, s.lastName, s.teamAbbrev));
        }
        for (Goalie g : goalieRepository.findAll()) {
            previousGoalieIds.add(g.id);
            previousLabels.put(g.id, label(g.firstName, g.lastName, g.teamAbbrev));
        }

        List<YahooPlayerResponse> players = yahooPlayerService.players(gameKey, season);
        if (players.isEmpty()) {
            // Never wipe the read model on an empty fetch — Yahoo is the only source.
            throw new IllegalStateException("Yahoo returned no players; preserving existing data");
        }

        Instant now = Instant.now();
        List<Skater> skaters = new ArrayList<>();
        List<Goalie> goalies = new ArrayList<>();
        for (YahooPlayerResponse player : players) {
            Long id = parseId(player.yahooId());
            if (id == null) {
                continue;
            }
            if (player.goalie()) {
                goalies.add(toGoalie(id, player, now));
            } else {
                skaters.add(toSkater(id, player, now));
            }
        }

        Map<Long, String> currentLabels = new HashMap<>();
        Set<Long> currentSkaterIds = new HashSet<>();
        Set<Long> currentGoalieIds = new HashSet<>();
        for (Skater s : skaters) {
            currentSkaterIds.add(s.id);
            currentLabels.put(s.id, label(s.firstName, s.lastName, s.teamAbbrev));
        }
        for (Goalie g : goalies) {
            currentGoalieIds.add(g.id);
            currentLabels.put(g.id, label(g.firstName, g.lastName, g.teamAbbrev));
        }

        skaterRepository.saveAll(skaters);
        goalieRepository.saveAll(goalies);
        skaterRepository.deleteAllById(minus(previousSkaterIds, currentSkaterIds));
        goalieRepository.deleteAllById(minus(previousGoalieIds, currentGoalieIds));

        Set<Long> previousIds = union(previousSkaterIds, previousGoalieIds);
        Set<Long> currentIds = union(currentSkaterIds, currentGoalieIds);
        List<String> added = labelsFor(minus(currentIds, previousIds), currentLabels);
        List<String> removed = labelsFor(minus(previousIds, currentIds), previousLabels);

        log.info("Player sync complete: {} skaters, {} goalies; +{} added, -{} removed",
                skaters.size(), goalies.size(), added.size(), removed.size());

        SyncRun run = new SyncRun();
        run.startedAt = started;
        run.finishedAt = Instant.now();
        run.status = "success";
        run.skaters = skaters.size();
        run.goalies = goalies.size();
        run.addedCount = added.size();
        run.removedCount = removed.size();
        run.added = toJson(added);
        run.removed = toJson(removed);
        return syncRunRepository.save(run);
    }

    public List<SyncRunResponse> getRuns(int limit) {
        return syncRunRepository.findAllByOrderByStartedAtDesc(Limit.of(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    private SyncRun failedRun(Instant started, Exception e) {
        SyncRun run = new SyncRun();
        run.startedAt = started;
        run.finishedAt = Instant.now();
        run.status = "failed";
        run.addedCount = 0;
        run.removedCount = 0;
        run.added = "[]";
        run.removed = "[]";
        run.error = e.toString();
        return run;
    }

    private SyncRunResponse toResponse(SyncRun r) {
        return new SyncRunResponse(r.id, r.startedAt, r.finishedAt, r.status,
                r.skaters, r.goalies,
                r.addedCount == null ? 0 : r.addedCount, r.removedCount == null ? 0 : r.removedCount,
                fromJson(r.added), fromJson(r.removed), r.error);
    }

    private static String label(String firstName, String lastName, String teamAbbrev) {
        String name = (firstName + " " + lastName).trim();
        return teamAbbrev == null || teamAbbrev.isBlank() ? name : name + " (" + teamAbbrev + ")";
    }

    private static List<String> labelsFor(Set<Long> ids, Map<Long, String> labels) {
        return ids.stream().map(labels::get).filter(Objects::nonNull).sorted().toList();
    }

    private static Set<Long> minus(Set<Long> a, Set<Long> b) {
        Set<Long> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static Set<Long> union(Set<Long> a, Set<Long> b) {
        Set<Long> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    private static Long parseId(String yahooId) {
        if (yahooId == null || yahooId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(yahooId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinPositions(List<String> positions) {
        return (positions == null || positions.isEmpty()) ? null : String.join(",", positions);
    }

    private static Double faceoffPct(Integer won, Integer lost) {
        if (won == null && lost == null) {
            return null;
        }
        int w = won == null ? 0 : won;
        int total = w + (lost == null ? 0 : lost);
        return total == 0 ? null : (double) w / total;
    }

    private static String toJson(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Skater toSkater(Long id, YahooPlayerResponse p, Instant now) {
        Skater e = new Skater();
        e.id = id;
        e.firstName = p.firstName();
        e.lastName = p.lastName();
        e.position = p.position();
        e.sweaterNumber = p.uniformNumber();
        e.teamAbbrev = p.teamAbbrev();
        e.headshot = p.imageUrl();
        YahooSkaterStats stats = p.skaterStats();
        if (stats != null) {
            e.gamesPlayed = stats.gamesPlayed();
            e.goals = stats.goals();
            e.assists = stats.assists();
            e.points = stats.points();
            e.plusMinus = stats.plusMinus();
            e.pim = stats.pim();
            e.powerPlayGoals = stats.powerPlayGoals();
            e.powerPlayPoints = stats.powerPlayPoints();
            e.shorthandedGoals = stats.shorthandedGoals();
            e.shorthandedPoints = stats.shorthandedPoints();
            e.gameWinningGoals = stats.gameWinningGoals();
            e.shots = stats.shotsOnGoal();
            e.shootingPctg = stats.shootingPct();
            e.avgToi = stats.avgTimeOnIce();
            e.hits = stats.hits();
            e.blockedShots = stats.blocks();
            e.totalFaceoffWins = stats.faceoffsWon();
            e.totalFaceoffLosses = stats.faceoffsLost();
            e.faceoffWinningPctg = faceoffPct(stats.faceoffsWon(), stats.faceoffsLost());
        }
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }

    private static Goalie toGoalie(Long id, YahooPlayerResponse p, Instant now) {
        Goalie e = new Goalie();
        e.id = id;
        e.firstName = p.firstName();
        e.lastName = p.lastName();
        e.position = p.position();
        e.sweaterNumber = p.uniformNumber();
        e.teamAbbrev = p.teamAbbrev();
        e.headshot = p.imageUrl();
        YahooGoalieStats stats = p.goalieStats();
        if (stats != null) {
            e.gamesPlayed = stats.gamesPlayed();
            e.gamesStarted = stats.gamesStarted();
            e.wins = stats.wins();
            e.losses = stats.losses();
            e.shutouts = stats.shutouts();
            e.shotsAgainst = stats.shotsAgainst();
            e.saves = stats.saves();
            e.goalsAgainst = stats.goalsAgainst();
            e.goalsAgainstAvg = stats.goalsAgainstAvg();
            e.savePctg = stats.savePct();
        }
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }
}
