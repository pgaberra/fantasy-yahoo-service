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
 *
 * <p>A sync is a full replace, so a fetch that comes back without a stat line would blank the
 * one we hold. That matters between seasons: the roster we want is the new season's, while the
 * stats we want are last season's, which are finished and cannot change. With
 * {@code sync.refresh-stats=false} a run therefore refreshes identity — name, team, number,
 * eligible positions, headshot — and carries each player's stored stat line across untouched.
 * A player new to the pool has none to carry, which is the right answer for a rookie with no
 * season behind them. Turn it back on when the stats being cached are ones that still move.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    // Own Jackson 2 mapper for the added/removed JSON columns: Spring Boot 4's bean is
    // Jackson 3, so we don't inject one (same pattern as YahooFantasyClient).
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * How much of the pool a run has to come back with before its deletions are believed. The
     * player list is paginated, and a page that comes back short ends the walk early — which
     * looks exactly like several hundred players having left the league. That used to cost a
     * cache refresh; now that the BFF drops a departed player's row from every saved projection,
     * it would cost users their work. A season's turnover moves who is in the pool, not how many.
     */
    private static final double MIN_RETAINED_SHARE = 0.8;

    private final YahooPlayerService yahooPlayerService;
    private final SkaterRepository skaterRepository;
    private final GoalieRepository goalieRepository;
    private final SyncRunRepository syncRunRepository;
    private final String gameKey;
    private final String season;
    private final boolean refreshStats;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncService(YahooPlayerService yahooPlayerService,
                       SkaterRepository skaterRepository,
                       GoalieRepository goalieRepository,
                       SyncRunRepository syncRunRepository,
                       @Value("${sync.yahoo-game-key:nhl}") String gameKey,
                       @Value("${sync.yahoo-season:}") String season,
                       @Value("${sync.refresh-stats:true}") boolean refreshStats) {
        this.yahooPlayerService = yahooPlayerService;
        this.skaterRepository = skaterRepository;
        this.goalieRepository = goalieRepository;
        this.syncRunRepository = syncRunRepository;
        this.gameKey = gameKey;
        this.season = season;
        this.refreshStats = refreshStats;
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
        Map<Long, Skater> previousSkaters = new HashMap<>();
        Map<Long, Goalie> previousGoalies = new HashMap<>();
        for (Skater s : skaterRepository.findAll()) {
            previousSkaters.put(s.id, s);
            previousLabels.put(s.id, label(s.firstName, s.lastName, s.teamAbbrev));
        }
        for (Goalie g : goalieRepository.findAll()) {
            previousGoalies.put(g.id, g);
            previousLabels.put(g.id, label(g.firstName, g.lastName, g.teamAbbrev));
        }
        Set<Long> previousSkaterIds = new HashSet<>(previousSkaters.keySet());
        Set<Long> previousGoalieIds = new HashSet<>(previousGoalies.keySet());

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
                goalies.add(toGoalie(id, player, now, previousGoalies.get(id)));
            } else {
                skaters.add(toSkater(id, player, now, previousSkaters.get(id)));
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

        int previousCount = previousSkaterIds.size() + previousGoalieIds.size();
        int currentCount = currentSkaterIds.size() + currentGoalieIds.size();
        if (previousCount > 0 && currentCount < previousCount * MIN_RETAINED_SHARE) {
            throw new IllegalStateException("Yahoo returned only " + currentCount + " players against "
                    + previousCount + " held; preserving existing data");
        }

        skaterRepository.saveAll(skaters);
        goalieRepository.saveAll(goalies);
        skaterRepository.deleteAllById(minus(previousSkaterIds, currentSkaterIds));
        goalieRepository.deleteAllById(minus(previousGoalieIds, currentGoalieIds));

        Set<Long> previousIds = union(previousSkaterIds, previousGoalieIds);
        Set<Long> currentIds = union(currentSkaterIds, currentGoalieIds);
        List<String> added = labelsFor(minus(currentIds, previousIds), currentLabels);
        List<String> removed = labelsFor(minus(previousIds, currentIds), previousLabels);

        log.info("Player sync complete: {} skaters, {} goalies; +{} added, -{} removed; "
                        + "stat lines {}",
                skaters.size(), goalies.size(), added.size(), removed.size(),
                refreshStats ? "refreshed" : "carried across");

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

    private Skater toSkater(Long id, YahooPlayerResponse p, Instant now, Skater previous) {
        Skater e = new Skater();
        e.id = id;
        e.firstName = p.firstName();
        e.lastName = p.lastName();
        e.position = p.position();
        e.sweaterNumber = p.uniformNumber();
        e.teamAbbrev = p.teamAbbrev();
        e.headshot = p.imageUrl();
        if (refreshStats) {
            applyStats(e, p.skaterStats());
        } else {
            carryStats(e, previous);
        }
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }

    private static void applyStats(Skater e, YahooSkaterStats stats) {
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
    }

    /** A player the pool has just gained has no stored line, and none is invented for them. */
    private static void carryStats(Skater e, Skater previous) {
        if (previous == null) {
            return;
        }
        e.gamesPlayed = previous.gamesPlayed;
        e.goals = previous.goals;
        e.assists = previous.assists;
        e.points = previous.points;
        e.plusMinus = previous.plusMinus;
        e.pim = previous.pim;
        e.powerPlayGoals = previous.powerPlayGoals;
        e.powerPlayPoints = previous.powerPlayPoints;
        e.shorthandedGoals = previous.shorthandedGoals;
        e.shorthandedPoints = previous.shorthandedPoints;
        e.gameWinningGoals = previous.gameWinningGoals;
        e.shots = previous.shots;
        e.shootingPctg = previous.shootingPctg;
        e.avgToi = previous.avgToi;
        e.hits = previous.hits;
        e.blockedShots = previous.blockedShots;
        e.totalFaceoffWins = previous.totalFaceoffWins;
        e.totalFaceoffLosses = previous.totalFaceoffLosses;
        e.faceoffWinningPctg = previous.faceoffWinningPctg;
    }

    private Goalie toGoalie(Long id, YahooPlayerResponse p, Instant now, Goalie previous) {
        Goalie e = new Goalie();
        e.id = id;
        e.firstName = p.firstName();
        e.lastName = p.lastName();
        e.position = p.position();
        e.sweaterNumber = p.uniformNumber();
        e.teamAbbrev = p.teamAbbrev();
        e.headshot = p.imageUrl();
        if (refreshStats) {
            applyStats(e, p.goalieStats());
        } else {
            carryStats(e, previous);
        }
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }

    private static void applyStats(Goalie e, YahooGoalieStats stats) {
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
    }

    private static void carryStats(Goalie e, Goalie previous) {
        if (previous == null) {
            return;
        }
        e.gamesPlayed = previous.gamesPlayed;
        e.gamesStarted = previous.gamesStarted;
        e.wins = previous.wins;
        e.losses = previous.losses;
        e.shutouts = previous.shutouts;
        e.shotsAgainst = previous.shotsAgainst;
        e.saves = previous.saves;
        e.goalsAgainst = previous.goalsAgainst;
        e.goalsAgainstAvg = previous.goalsAgainstAvg;
        e.savePctg = previous.savePctg;
    }
}
