package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.GoalieResponse;
import com.fantasy.yahoo.players.dto.SkaterResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class PlayerService {

    private final SkaterRepository skaterRepository;
    private final GoalieRepository goalieRepository;

    public PlayerService(SkaterRepository skaterRepository, GoalieRepository goalieRepository) {
        this.skaterRepository = skaterRepository;
        this.goalieRepository = goalieRepository;
    }

    @Transactional(readOnly = true)
    public List<SkaterResponse> getSkaters() {
        return skaterRepository.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .map(PlayerService::toSkaterResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalieResponse> getGoalies() {
        return goalieRepository.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .map(PlayerService::toGoalieResponse)
                .toList();
    }

    private static SkaterResponse toSkaterResponse(Skater s) {
        return new SkaterResponse(
                s.id, s.firstName, s.lastName, s.position,
                eligiblePositions(s.yahooPositions, s.position),
                s.sweaterNumber, s.teamAbbrev, s.headshot,
                s.gamesPlayed, s.goals, s.assists, s.points, s.plusMinus, s.pim,
                s.powerPlayGoals, s.powerPlayPoints, s.shorthandedGoals, s.shorthandedPoints,
                s.gameWinningGoals, s.shots, s.shootingPctg, s.avgToi,
                s.faceoffWinningPctg, s.hits, s.blockedShots,
                s.totalFaceoffWins, s.totalFaceoffLosses);
    }

    private static GoalieResponse toGoalieResponse(Goalie g) {
        return new GoalieResponse(
                g.id, g.firstName, g.lastName, g.position,
                eligiblePositions(g.yahooPositions, "G"),
                g.sweaterNumber, g.teamAbbrev, g.headshot,
                g.gamesPlayed, g.gamesStarted, g.wins, g.losses, g.shutouts,
                g.shotsAgainst, g.saves, g.goalsAgainst, g.goalsAgainstAvg, g.savePctg);
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
