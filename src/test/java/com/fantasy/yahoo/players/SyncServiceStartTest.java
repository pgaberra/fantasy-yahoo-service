package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooLeagueService;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.player.YahooPlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who gets to start a sync. The flag used to be claimed on the sync's own thread, after the
 * trigger had already answered 202, so a second trigger in that gap was also told yes and its
 * thread then logged "already running" at ERROR.
 */
class SyncServiceStartTest {

    private YahooLeagueService leagueService;
    private SyncRunRepository syncRunRepository;
    private SyncService service;

    @BeforeEach
    void setUp() {
        leagueService = mock(YahooLeagueService.class);
        syncRunRepository = mock(SyncRunRepository.class);
        service = new SyncService(mock(YahooPlayerService.class), leagueService,
                mock(SkaterRepository.class), mock(GoalieRepository.class),
                mock(SkaterSeasonRepository.class), mock(GoalieSeasonRepository.class),
                syncRunRepository, mock(HeadshotSyncService.class), 2025);
    }

    @Test
    void startAsync_claimsTheRunBeforeItReturns() throws Exception {
        CountDownLatch release = blockInsideTheRun();

        assertThat(service.startAsync()).isTrue();
        assertThat(service.isRunning()).isTrue();

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    @Test
    void startAsync_refusesASecondRunWhileOneIsUnderWay() throws Exception {
        CountDownLatch release = blockInsideTheRun();
        service.startAsync();

        assertThat(service.startAsync()).isFalse();

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    /** The scheduler overlapping a triggered run: nothing to record, nothing thrown. */
    @Test
    void sync_whileARunIsUnderWay_isEmptyAndRecordsNoFailure() throws Exception {
        CountDownLatch release = blockInsideTheRun();
        service.startAsync();

        assertThat(service.sync()).isEmpty();
        verify(syncRunRepository, never()).save(any());

        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
    }

    /** A run that fails on its own thread must still let the next one start. */
    @Test
    void aFailedRun_stopsBeingRunning() {
        when(leagueService.leagues(any())).thenThrow(new IllegalStateException("Yahoo refused"));

        assertThat(service.startAsync()).isTrue();

        await().atMost(5, TimeUnit.SECONDS).until(() -> !service.isRunning());
        assertThat(service.startAsync()).isTrue();
    }

    private CountDownLatch blockInsideTheRun() {
        CountDownLatch release = new CountDownLatch(1);
        when(leagueService.leagues(any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return new LeaguesResponse(List.of());
        });
        return release;
    }
}
