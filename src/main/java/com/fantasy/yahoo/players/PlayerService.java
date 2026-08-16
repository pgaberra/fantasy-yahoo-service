package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.GoalieResponse;
import com.fantasy.yahoo.players.dto.SkaterResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Serves the player pool as it is now, with the stat line of whichever season the caller asks
 * for. A player with no row for that season comes back with the identity and no numbers, which
 * is the honest answer for a rookie, a call-up, or anyone who did not play that year.
 */
@Service
public class PlayerService {

    private final SkaterRepository skaterRepository;
    private final GoalieRepository goalieRepository;
    private final SkaterSeasonRepository skaterSeasonRepository;
    private final GoalieSeasonRepository goalieSeasonRepository;
    private final PlayerHeadshotRepository headshotRepository;

    public PlayerService(SkaterRepository skaterRepository,
                         GoalieRepository goalieRepository,
                         SkaterSeasonRepository skaterSeasonRepository,
                         GoalieSeasonRepository goalieSeasonRepository,
                         PlayerHeadshotRepository headshotRepository) {
        this.skaterRepository = skaterRepository;
        this.goalieRepository = goalieRepository;
        this.skaterSeasonRepository = skaterSeasonRepository;
        this.goalieSeasonRepository = goalieSeasonRepository;
        this.headshotRepository = headshotRepository;
    }

    @Transactional(readOnly = true)
    public List<SkaterResponse> getSkaters(int season) {
        Map<Long, SkaterSeason> stats = new HashMap<>();
        for (SkaterSeason line : skaterSeasonRepository.findAllBySeason(season)) {
            stats.put(line.playerId, line);
        }
        Set<Long> withHeadshot = playerIdsWithHeadshot();
        return skaterRepository.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .map(skater -> toSkaterResponse(skater, stats.get(skater.id),
                        withHeadshot.contains(skater.id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalieResponse> getGoalies(int season) {
        Map<Long, GoalieSeason> stats = new HashMap<>();
        for (GoalieSeason line : goalieSeasonRepository.findAllBySeason(season)) {
            stats.put(line.playerId, line);
        }
        Set<Long> withHeadshot = playerIdsWithHeadshot();
        return goalieRepository.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .map(goalie -> toGoalieResponse(goalie, stats.get(goalie.id),
                        withHeadshot.contains(goalie.id)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PlayerHeadshot> getHeadshot(long playerId) {
        return headshotRepository.findById(playerId);
    }

    private Set<Long> playerIdsWithHeadshot() {
        return new HashSet<>(headshotRepository.findAllPlayerIds());
    }

    private static SkaterResponse toSkaterResponse(Skater s, SkaterSeason line, boolean hasHeadshot) {
        SkaterSeason stats = line == null ? new SkaterSeason() : line;
        return new SkaterResponse(
                s.id, s.firstName, s.lastName, s.position,
                eligiblePositions(s.yahooPositions, s.position),
                s.sweaterNumber, s.teamAbbrev, hasHeadshot ? s.headshot : null,
                stats.gamesPlayed, stats.goals, stats.assists, stats.points, stats.plusMinus,
                stats.pim, stats.powerPlayGoals, stats.powerPlayPoints, stats.shorthandedGoals,
                stats.shorthandedPoints, stats.gameWinningGoals, stats.shots, stats.shootingPctg,
                stats.avgToi, stats.faceoffWinningPctg, stats.hits, stats.blockedShots,
                stats.totalFaceoffWins, stats.totalFaceoffLosses);
    }

    private static GoalieResponse toGoalieResponse(Goalie g, GoalieSeason line, boolean hasHeadshot) {
        GoalieSeason stats = line == null ? new GoalieSeason() : line;
        return new GoalieResponse(
                g.id, g.firstName, g.lastName, g.position,
                eligiblePositions(g.yahooPositions, "G"),
                g.sweaterNumber, g.teamAbbrev, hasHeadshot ? g.headshot : null,
                stats.gamesPlayed, stats.gamesStarted, stats.wins, stats.losses, stats.shutouts,
                stats.shotsAgainst, stats.saves, stats.goalsAgainst, stats.goalsAgainstAvg,
                stats.savePctg);
    }

    /** Yahoo eligible positions (comma-joined), or the player's primary position if absent. */
    private static List<String> eligiblePositions(String yahooPositions, String fallback) {
        if (yahooPositions == null || yahooPositions.isBlank()) {
            return List.of(fallback);
        }
        return Arrays.stream(yahooPositions.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }
}
