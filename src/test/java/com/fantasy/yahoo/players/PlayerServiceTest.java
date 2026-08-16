package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.SkaterResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The pool is one thing and a season's numbers are another, so a read joins them: every player
 * who is in the league now, carrying the line for the season that was asked about.
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private SkaterRepository skaterRepository;

    @Mock
    private GoalieRepository goalieRepository;

    @Mock
    private SkaterSeasonRepository skaterSeasonRepository;

    @Mock
    private GoalieSeasonRepository goalieSeasonRepository;

    private PlayerService playerService() {
        return new PlayerService(skaterRepository, goalieRepository,
                skaterSeasonRepository, goalieSeasonRepository);
    }

    @Test
    void carriesTheAskedForSeasonsStatLine() {
        when(skaterRepository.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of(skater(1)));
        when(skaterSeasonRepository.findAllBySeason(2025)).thenReturn(List.of(line(1, 2025, 82, 64)));

        SkaterResponse response = playerService().getSkaters(2025).getFirst();

        assertThat(response.gamesPlayed()).isEqualTo(82);
        assertThat(response.goals()).isEqualTo(64);
    }

    /**
     * Someone who did not play that season is still in the league, so they are still in the
     * list — with nothing to report rather than left out or zeroed.
     */
    @Test
    void servesAPlayerWithNoLineForThatSeasonWithoutStats() {
        when(skaterRepository.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of(skater(1)));
        when(skaterSeasonRepository.findAllBySeason(2026)).thenReturn(List.of());

        List<SkaterResponse> skaters = playerService().getSkaters(2026);

        assertThat(skaters).hasSize(1);
        assertThat(skaters.getFirst().firstName()).isEqualTo("Connor");
        assertThat(skaters.getFirst().gamesPlayed()).isNull();
        assertThat(skaters.getFirst().goals()).isNull();
    }

    @Test
    void neverMixesOneSeasonsNumbersIntoAnother() {
        when(skaterRepository.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of(skater(1)));
        when(skaterSeasonRepository.findAllBySeason(2026)).thenReturn(List.of(line(1, 2026, 4, 1)));

        assertThat(playerService().getSkaters(2026).getFirst().gamesPlayed()).isEqualTo(4);
    }

    private static Skater skater(long id) {
        Skater skater = new Skater();
        skater.id = id;
        skater.firstName = "Connor";
        skater.lastName = "McDavid";
        skater.position = "C";
        skater.teamAbbrev = "EDM";
        skater.yahooPositions = "C,LW";
        skater.syncedAt = Instant.parse("2026-08-16T04:00:00Z");
        return skater;
    }

    private static SkaterSeason line(long playerId, int season, int gamesPlayed, int goals) {
        SkaterSeason skaterSeason = new SkaterSeason();
        skaterSeason.playerId = playerId;
        skaterSeason.season = season;
        skaterSeason.gamesPlayed = gamesPlayed;
        skaterSeason.goals = goals;
        skaterSeason.syncedAt = Instant.parse("2026-08-16T04:00:00Z");
        return skaterSeason;
    }
}
