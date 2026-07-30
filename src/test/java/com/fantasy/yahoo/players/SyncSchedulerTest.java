package com.fantasy.yahoo.players;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock
    private SyncService syncService;

    @Test
    void runsWhenEnabledAndNotDisabled() {
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
}
