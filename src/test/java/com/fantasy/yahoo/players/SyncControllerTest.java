package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooLeagueService;
import com.fantasy.yahoo.players.dto.SyncAcceptedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncControllerTest {

    private SyncService syncService;
    private SyncController controller;

    @BeforeEach
    void setUp() {
        syncService = mock(SyncService.class);
        controller = new SyncController(syncService, mock(YahooProbeService.class),
                mock(YahooLeagueService.class));
    }

    @Test
    void triggerSync_whenThisCallStartedIt_isAccepted() {
        when(syncService.startAsync()).thenReturn(true);

        ResponseEntity<SyncAcceptedResponse> response = controller.triggerSync();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void triggerSync_whenOneIsAlreadyRunning_isAConflict() {
        when(syncService.startAsync()).thenReturn(false);

        ResponseEntity<SyncAcceptedResponse> response = controller.triggerSync();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
