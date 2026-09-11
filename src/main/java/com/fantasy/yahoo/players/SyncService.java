package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooLeagueService;
import com.fantasy.yahoo.league.dto.LeagueSummary;
import com.fantasy.yahoo.oauth.YahooOAuthService;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes the cached player read model from Yahoo. Pulls the full league-wide player list
 * straight from {@link YahooPlayerService} (no inter-service HTTP), splits skaters/goalies by
 * Yahoo's position type, upserts and deletes stale rows, and records each run + diff. If the
 * Yahoo fetch fails or returns nothing the run is recorded as failed and existing data is kept.
 *
 * <p>A run writes two things: the player pool as it is now, and the stat line for the one season
 * it is collecting ({@code sync.yahoo-season}). Earlier seasons are never touched, so pointing
 * the sync at a new season starts filling that one in while the finished one stays exactly as
 * it was — which is what lets the app collect this season and still show last season's numbers.
 *
 * <p>The pool is read <b>through a league</b> the service account belongs to, not through the
 * game. That is the only route the Fantasy API documents — its own client offers no game-wide
 * player listing — and the game-wide collection we used until June 2026 is now refused outright.
 * The league is discovered rather than configured: a league key contains the game key, so it
 * changes every season, and pinning one would mean editing config each autumn. Join the service
 * account to a league for the season and the sync finds it.
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
    private final YahooLeagueService leagueService;
    private final SkaterRepository skaterRepository;
    private final GoalieRepository goalieRepository;
    private final SkaterSeasonRepository skaterSeasonRepository;
    private final GoalieSeasonRepository goalieSeasonRepository;
    private final SyncRunRepository syncRunRepository;
    private final HeadshotSyncService headshotSyncService;
    private final int season;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncService(YahooPlayerService yahooPlayerService,
                       YahooLeagueService leagueService,
                       SkaterRepository skaterRepository,
                       GoalieRepository goalieRepository,
                       SkaterSeasonRepository skaterSeasonRepository,
                       GoalieSeasonRepository goalieSeasonRepository,
                       SyncRunRepository syncRunRepository,
                       HeadshotSyncService headshotSyncService,
                       @Value("${sync.yahoo-season}") int season) {
        this.yahooPlayerService = yahooPlayerService;
        this.leagueService = leagueService;
        this.skaterRepository = skaterRepository;
        this.goalieRepository = goalieRepository;
        this.skaterSeasonRepository = skaterSeasonRepository;
        this.goalieSeasonRepository = goalieSeasonRepository;
        this.syncRunRepository = syncRunRepository;
        this.headshotSyncService = headshotSyncService;
        this.season = season;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a sync on its own thread and returns as soon as it is under way.
     *
     * <p>The flag is claimed here, before the thread starts, so a caller told it started can rely
     * on that, and a second caller in the same instant is refused rather than also told yes.
     *
     * @return whether this call is the one that started it; false when a sync is already running
     */
    public boolean startAsync() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread.ofVirtual().name("player-sync").start(() -> {
            try {
                runClaimed();
            } catch (Exception e) {
                log.error("Triggered player sync failed", e);
            }
        });
        return true;
    }

    /**
     * Runs a sync and waits for it. For the scheduler, which has nobody to answer to.
     *
     * @return the recorded run, or empty when a sync was already running. That is an ordinary
     *     outcome, not a fault: the run under way is doing the same work.
     */
    public Optional<SyncRun> sync() {
        if (!running.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.ofNullable(runClaimed());
    }

    private SyncRun runClaimed() {
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

        String leagueKey = leagueToReadThrough();
        List<YahooPlayerResponse> players =
                yahooPlayerService.leaguePlayers(leagueKey, String.valueOf(season));
        if (players.isEmpty()) {
            // Never wipe the read model on an empty fetch — Yahoo is the only source.
            throw new IllegalStateException("Yahoo returned no players; preserving existing data");
        }

        Instant now = Instant.now();
        List<Skater> skaters = new ArrayList<>();
        List<Goalie> goalies = new ArrayList<>();
        List<SkaterSeason> skaterStats = new ArrayList<>();
        List<GoalieSeason> goalieStats = new ArrayList<>();
        for (YahooPlayerResponse player : players) {
            Long id = parseId(player.yahooId());
            if (id == null) {
                continue;
            }
            if (player.goalie()) {
                goalies.add(toGoalie(id, player, now));
                goalieStats.add(toGoalieSeason(id, player.goalieStats(), now));
            } else {
                skaters.add(toSkater(id, player, now));
                skaterStats.add(toSkaterSeason(id, player.skaterStats(), now));
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
        skaterSeasonRepository.saveAll(skaterStats);
        goalieSeasonRepository.saveAll(goalieStats);
        // The stat rows of a departed player go with them: the tables cascade on the identity.
        skaterRepository.deleteAllById(minus(previousSkaterIds, currentSkaterIds));
        goalieRepository.deleteAllById(minus(previousGoalieIds, currentGoalieIds));

        refreshHeadshots(skaters, goalies);

        Set<Long> previousIds = union(previousSkaterIds, previousGoalieIds);
        Set<Long> currentIds = union(currentSkaterIds, currentGoalieIds);
        List<String> added = labelsFor(minus(currentIds, previousIds), currentLabels);
        List<String> removed = labelsFor(minus(previousIds, currentIds), previousLabels);

        log.info("Player sync complete: {} skaters, {} goalies; +{} added, -{} removed; "
                        + "stat lines written for {}",
                skaters.size(), goalies.size(), added.size(), removed.size(), season);

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

    /**
     * The read model is what a sync exists to produce; the thumbnails are a rendering of it. An
     * image CDN that will not answer must therefore not turn a successful sync into a failed one
     * — the next run picks the images up again.
     */
    private void refreshHeadshots(List<Skater> skaters, List<Goalie> goalies) {
        Map<Long, String> sources = new HashMap<>();
        for (Skater s : skaters) {
            if (s.headshot != null && !s.headshot.isBlank()) {
                sources.put(s.id, s.headshot);
            }
        }
        for (Goalie g : goalies) {
            if (g.headshot != null && !g.headshot.isBlank()) {
                sources.put(g.id, g.headshot);
            }
        }
        try {
            headshotSyncService.refresh(sources);
        } catch (Exception e) {
            log.error("Headshot refresh failed; player data was synced without it", e);
        }
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

    /**
     * A league of the service account's for the season being collected. Preferring that season
     * matters: a league key belongs to one season's game, and reading last season's league would
     * quietly cache last season's roster. Falling back to any league is better than failing —
     * the collection is the game's player universe either way — but it is worth saying out loud.
     */
    private String leagueToReadThrough() {
        List<LeagueSummary> leagues = leagueService.leagues(YahooOAuthService.SERVICE_ACCOUNT_ID)
                .leagues();
        if (leagues.isEmpty()) {
            throw new IllegalStateException(
                    "The Yahoo service account is in no leagues, so there is nothing to read the "
                            + "player pool through. Join it to a league for season " + season + ".");
        }
        for (LeagueSummary league : leagues) {
            if (league.season() != null && league.season() == season) {
                return league.leagueKey();
            }
        }
        LeagueSummary fallback = leagues.getFirst();
        // The key comes from Yahoo, so it is stripped of CR/LF before it reaches the log.
        log.warn("No league found for season {}; reading the pool through {} (season {}) instead",
                season, forLog(fallback.leagueKey()), fallback.season());
        return fallback.leagueKey();
    }

    /** Yahoo's own text on one line: lines() splits on CR, LF and CRLF, and joining drops them. */
    private static String forLog(String value) {
        return value == null ? "" : String.join(" ", value.lines().toList());
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
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }

    private SkaterSeason toSkaterSeason(Long id, YahooSkaterStats stats, Instant now) {
        SkaterSeason e = new SkaterSeason();
        e.playerId = id;
        e.season = season;
        e.syncedAt = now;
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
        e.yahooPositions = joinPositions(p.eligiblePositions());
        e.syncedAt = now;
        return e;
    }

    private GoalieSeason toGoalieSeason(Long id, YahooGoalieStats stats, Instant now) {
        GoalieSeason e = new GoalieSeason();
        e.playerId = id;
        e.season = season;
        e.syncedAt = now;
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
        return e;
    }
}
