package com.fantasy.yahoo.players;

import com.fantasy.yahoo.player.YahooPlayerService;
import com.fantasy.yahoo.player.dto.YahooGoalieStats;
import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import com.fantasy.yahoo.player.dto.YahooSkaterStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A sync is a full replace, which is fine while the stats being cached are the ones Yahoo is
 * still updating. Between seasons it is not: the roster worth caching is the new season's while
 * the stats worth caching are last season's, finished and unobtainable from the new season's
 * game — so a fetch without a stat line must not blank the one we hold.
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private YahooPlayerService yahooPlayerService;

    @Mock
    private SkaterRepository skaterRepository;

    @Mock
    private GoalieRepository goalieRepository;

    @Mock
    private SyncRunRepository syncRunRepository;

    private SyncService syncService(boolean refreshStats) {
        return new SyncService(yahooPlayerService, skaterRepository, goalieRepository,
                syncRunRepository, "nhl", "2025", refreshStats);
    }

    @Test
    void carriesAStoredStatLineAcrossWhenStatsAreNotBeingRefreshed() {
        givenStored(storedSkater(9, 82, 64), storedGoalie(101, 58, 36));
        givenYahooReturns(skaterWithoutStats(9, "Edmonton"), goalieWithoutStats(101));

        syncService(false).sync();

        Skater skater = savedSkaters().getFirst();
        assertThat(skater.goals).isEqualTo(64);
        assertThat(skater.gamesPlayed).isEqualTo(82);
        // Identity is still refreshed — that is the whole point of running the sync.
        assertThat(skater.teamAbbrev).isEqualTo("Edmonton");
        assertThat(savedGoalies().getFirst().wins).isEqualTo(36);
    }

    /** A rookie has no stored line to carry, and none is invented for them. */
    @Test
    void leavesAPlayerNewToThePoolWithoutAStatLine() {
        givenStored();
        givenYahooReturns(skaterWithoutStats(9, "Edmonton"));

        syncService(false).sync();

        assertThat(savedSkaters().getFirst().goals).isNull();
    }

    @Test
    void writesTheFetchedStatsWhenStatsAreBeingRefreshed() {
        givenStored(storedSkater(9, 82, 64));
        givenYahooReturns(skaterWithStats(9, "Edmonton", 12, 3));

        syncService(true).sync();

        Skater skater = savedSkaters().getFirst();
        assertThat(skater.goals).isEqualTo(3);
        assertThat(skater.gamesPlayed).isEqualTo(12);
    }

    private void givenStored(Object... players) {
        List<Skater> skaters = List.of(players).stream()
                .filter(Skater.class::isInstance).map(Skater.class::cast).toList();
        List<Goalie> goalies = List.of(players).stream()
                .filter(Goalie.class::isInstance).map(Goalie.class::cast).toList();
        when(skaterRepository.findAll()).thenReturn(skaters);
        when(goalieRepository.findAll()).thenReturn(goalies);
    }

    private void givenYahooReturns(YahooPlayerResponse... players) {
        when(yahooPlayerService.players("nhl", "2025")).thenReturn(List.of(players));
    }

    @SuppressWarnings("unchecked")
    private List<Skater> savedSkaters() {
        ArgumentCaptor<List<Skater>> saved = ArgumentCaptor.forClass(List.class);
        verify(skaterRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Goalie> savedGoalies() {
        ArgumentCaptor<List<Goalie>> saved = ArgumentCaptor.forClass(List.class);
        verify(goalieRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    private static Skater storedSkater(long id, int gamesPlayed, int goals) {
        Skater skater = new Skater();
        skater.id = id;
        skater.firstName = "Connor";
        skater.lastName = "McDavid";
        skater.teamAbbrev = "EDM";
        skater.gamesPlayed = gamesPlayed;
        skater.goals = goals;
        skater.syncedAt = Instant.parse("2026-06-01T04:00:00Z");
        return skater;
    }

    private static Goalie storedGoalie(long id, int gamesPlayed, int wins) {
        Goalie goalie = new Goalie();
        goalie.id = id;
        goalie.firstName = "Igor";
        goalie.lastName = "Shesterkin";
        goalie.teamAbbrev = "NYR";
        goalie.gamesPlayed = gamesPlayed;
        goalie.wins = wins;
        goalie.syncedAt = Instant.parse("2026-06-01T04:00:00Z");
        return goalie;
    }

    private static YahooPlayerResponse skaterWithoutStats(long id, String team) {
        return player(id, team, false, null, null);
    }

    private static YahooPlayerResponse skaterWithStats(long id, String team, int gamesPlayed, int goals) {
        return player(id, team, false, new YahooSkaterStats(gamesPlayed, goals, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null), null);
    }

    private static YahooPlayerResponse goalieWithoutStats(long id) {
        return player(id, "NYR", true, null, null);
    }

    private static YahooPlayerResponse player(long id, String team, boolean goalie,
                                              YahooSkaterStats skaterStats, YahooGoalieStats goalieStats) {
        return new YahooPlayerResponse(
                String.valueOf(id), "Connor McDavid", "Connor", "McDavid", team,
                goalie ? "G" : "C", 97, "https://example.test/headshot.png", goalie,
                List.of(goalie ? "G" : "C"), skaterStats, goalieStats);
    }
}
