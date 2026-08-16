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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A run caches two different things with two different lifetimes: the pool as it is now, which
 * turns over, and one season's stat line, which stops changing when the season ends. Keeping
 * them apart is what lets the app collect the season being played while still showing the one
 * that finished.
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private static final int SEASON = 2026;

    @Mock
    private YahooPlayerService yahooPlayerService;

    @Mock
    private SkaterRepository skaterRepository;

    @Mock
    private GoalieRepository goalieRepository;

    @Mock
    private SkaterSeasonRepository skaterSeasonRepository;

    @Mock
    private GoalieSeasonRepository goalieSeasonRepository;

    @Mock
    private SyncRunRepository syncRunRepository;

    @Mock
    private HeadshotSyncService headshotSyncService;

    private SyncService syncService() {
        return new SyncService(yahooPlayerService, skaterRepository, goalieRepository,
                skaterSeasonRepository, goalieSeasonRepository, syncRunRepository,
                headshotSyncService, SEASON);
    }

    @Test
    void writesTheStatLineUnderTheSeasonBeingCollected() {
        givenStored(storedSkater(9));
        givenYahooReturns(skaterWithStats(9, "EDM", 12, 3));

        syncService().sync();

        SkaterSeason line = savedSkaterSeasons().getFirst();
        assertThat(line.playerId).isEqualTo(9L);
        assertThat(line.season).isEqualTo(SEASON);
        assertThat(line.gamesPlayed).isEqualTo(12);
        assertThat(line.goals).isEqualTo(3);
    }

    /**
     * The whole point of the split: collecting a new season must not disturb a finished one.
     * Nothing here deletes a stat row — only a player leaving the pool does, by cascade.
     */
    @Test
    void leavesEarlierSeasonsAlone() {
        givenStored(storedSkater(9));
        givenYahooReturns(skaterWithStats(9, "EDM", 12, 3));

        syncService().sync();

        assertThat(savedSkaterSeasons()).allMatch(line -> line.season == SEASON);
        verify(skaterSeasonRepository, never()).deleteAll(any());
        verify(skaterSeasonRepository, never()).deleteAllById(any());
    }

    @Test
    void refreshesTheIdentitySeparatelyFromTheStats() {
        givenStored(storedSkater(9));
        givenYahooReturns(skaterWithStats(9, "BOS", 12, 3));

        syncService().sync();

        assertThat(savedSkaters().getFirst().teamAbbrev).isEqualTo("BOS");
    }

    /** A player with nothing to report gets a row of nulls, not a row of zeros. */
    @Test
    void recordsAPlayerWithoutStatsAsHavingNone() {
        givenStored();
        givenYahooReturns(skaterWithoutStats(9));

        syncService().sync();

        SkaterSeason line = savedSkaterSeasons().getFirst();
        assertThat(line.season).isEqualTo(SEASON);
        assertThat(line.gamesPlayed).isNull();
        assertThat(line.goals).isNull();
    }

    @Test
    void keepsGoaliesOnTheirOwnSeasonRows() {
        givenStored();
        givenYahooReturns(goalieWithStats(101, 40, 25));

        syncService().sync();

        GoalieSeason line = savedGoalieSeasons().getFirst();
        assertThat(line.season).isEqualTo(SEASON);
        assertThat(line.wins).isEqualTo(25);
    }

    /**
     * The player list is paginated and a short page ends the walk early, which looks just like a
     * mass exodus. Believing it would delete those players out of every saved projection.
     */
    @Test
    void refusesToActOnAFetchThatLostMostOfThePool() {
        givenStored(storedSkater(1), storedSkater(2), storedSkater(3), storedSkater(4), storedSkater(5));
        givenYahooReturns(skaterWithoutStats(1), skaterWithoutStats(2));

        syncService().sync();

        verify(skaterRepository, never()).saveAll(any());
        verify(skaterSeasonRepository, never()).saveAll(any());
        verify(skaterRepository, never()).deleteAllById(any());
        assertThat(savedRun().status).isEqualTo("failed");
        assertThat(savedRun().error).contains("preserving existing data");
    }

    @Test
    void asksYahooForTheSeasonBeingCollected() {
        givenStored();
        givenYahooReturns(skaterWithoutStats(9));

        syncService().sync();

        verify(yahooPlayerService).players("nhl", "2026");
    }

    @Test
    void handsEveryPlayerWithASourceImageToTheHeadshotRefresh() {
        givenStored();
        givenYahooReturns(skaterWithoutStats(9), goalieWithStats(101, 40, 25));

        syncService().sync();

        assertThat(refreshedSources())
                .containsOnlyKeys(9L, 101L)
                .containsValue("https://example.test/headshot.png");
    }

    /**
     * The read model is what a sync exists to produce; the thumbnails are a rendering of it. An
     * image CDN that will not answer must not turn a good sync into a failed one.
     */
    @Test
    void recordsASuccessfulSyncWhenTheHeadshotRefreshFails() {
        givenStored();
        givenYahooReturns(skaterWithoutStats(9));
        when(headshotSyncService.refresh(any())).thenThrow(new RuntimeException("image CDN down"));

        syncService().sync();

        assertThat(savedRun().status).isEqualTo("success");
        assertThat(savedSkaters()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> refreshedSources() {
        ArgumentCaptor<Map<Long, String>> refreshed = ArgumentCaptor.forClass(Map.class);
        verify(headshotSyncService).refresh(refreshed.capture());
        return refreshed.getValue();
    }

    private void givenStored(Skater... skaters) {
        when(skaterRepository.findAll()).thenReturn(List.of(skaters));
        when(goalieRepository.findAll()).thenReturn(List.of());
    }

    private void givenYahooReturns(YahooPlayerResponse... players) {
        when(yahooPlayerService.players("nhl", String.valueOf(SEASON))).thenReturn(List.of(players));
    }

    private SyncRun savedRun() {
        ArgumentCaptor<SyncRun> saved = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(saved.capture());
        return saved.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Skater> savedSkaters() {
        ArgumentCaptor<List<Skater>> saved = ArgumentCaptor.forClass(List.class);
        verify(skaterRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<SkaterSeason> savedSkaterSeasons() {
        ArgumentCaptor<List<SkaterSeason>> saved = ArgumentCaptor.forClass(List.class);
        verify(skaterSeasonRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<GoalieSeason> savedGoalieSeasons() {
        ArgumentCaptor<List<GoalieSeason>> saved = ArgumentCaptor.forClass(List.class);
        verify(goalieSeasonRepository).saveAll(saved.capture());
        return saved.getValue();
    }

    private static Skater storedSkater(long id) {
        Skater skater = new Skater();
        skater.id = id;
        skater.firstName = "Connor";
        skater.lastName = "McDavid";
        skater.teamAbbrev = "EDM";
        skater.syncedAt = Instant.parse("2026-06-01T04:00:00Z");
        return skater;
    }

    private static YahooPlayerResponse skaterWithoutStats(long id) {
        return player(id, "EDM", false, null, null);
    }

    private static YahooPlayerResponse skaterWithStats(long id, String team, int gamesPlayed, int goals) {
        return player(id, team, false, new YahooSkaterStats(gamesPlayed, goals, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null), null);
    }

    private static YahooPlayerResponse goalieWithStats(long id, int gamesPlayed, int wins) {
        return player(id, "NYR", true, null,
                new YahooGoalieStats(gamesPlayed, null, wins, null, null, null, null, null, null, null));
    }

    private static YahooPlayerResponse player(long id, String team, boolean goalie,
                                              YahooSkaterStats skaterStats, YahooGoalieStats goalieStats) {
        return new YahooPlayerResponse(
                String.valueOf(id), "Connor McDavid", "Connor", "McDavid", team,
                goalie ? "G" : "C", 97, "https://example.test/headshot.png", goalie,
                List.of(goalie ? "G" : "C"), skaterStats, goalieStats);
    }
}
