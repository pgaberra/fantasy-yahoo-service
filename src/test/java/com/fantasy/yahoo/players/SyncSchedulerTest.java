package com.fantasy.yahoo.players;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock
    private SyncService syncService;

    @Test
    void runsWhenEnabledAndNotDisabled() {
        when(syncService.sync()).thenReturn(Optional.of(new SyncRun()));
        new SyncScheduler(syncService, true, false).scheduledSync();
        verify(syncService).sync();
    }

    @Test
    void skipsWhenNotEnabled() {
        new SyncScheduler(syncService, false, false).scheduledSync();
        verify(syncService, never()).sync();
    }

    @Test
    void skipsWhenDisabledEvenIfEnabled() {
        new SyncScheduler(syncService, true, true).scheduledSync();
        verify(syncService, never()).sync();
    }

    /** A triggered sync still going at 04:00 is the same work already under way, not a failure. */
    @Test
    void overlappingATriggeredSync_isAQuietSkip() {
        when(syncService.sync()).thenReturn(Optional.empty());

        assertThatCode(() -> new SyncScheduler(syncService, true, false).scheduledSync())
                .doesNotThrowAnyException();
    }
}
