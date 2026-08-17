package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.SyncAcceptedResponse;
import com.fantasy.yahoo.players.dto.SyncRunResponse;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Sync", description = "Refresh the cached player read model from Yahoo")
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final YahooProbeService probeService;

    public SyncController(SyncService syncService, YahooProbeService probeService) {
        this.syncService = syncService;
        this.probeService = probeService;
    }

    @Operation(summary = "Trigger a player sync",
            description = "Refreshes the cached player read model from Yahoo. Runs asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Sync started"),
            @ApiResponse(responseCode = "409", description = "A sync is already running")
    })
    @PostMapping
    public ResponseEntity<SyncAcceptedResponse> triggerSync() {
        if (syncService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SyncAcceptedResponse("running"));
        }
        Thread.ofVirtual().name("player-sync").start(() -> {
            try {
                syncService.sync();
            } catch (Exception e) {
                log.error("Triggered player sync failed", e);
            }
        });
        return ResponseEntity.accepted().body(new SyncAcceptedResponse("accepted"));
    }

    @Operation(summary = "Recent sync runs",
            description = "Outcome (counts) and the diff (players added/removed) of the most recent syncs.")
    @ApiResponse(responseCode = "200", description = "Runs returned")
    @GetMapping("/runs")
    public List<SyncRunResponse> runs(@RequestParam(defaultValue = "10") int limit) {
        return syncService.getRuns(Math.max(1, Math.min(limit, 50)));
    }

    @Operation(summary = "Ask Yahoo whether the service account may read a game's players",
            description = "Makes one live call and reports the status Yahoo answered with, "
                    + "including its own error wording. Reads nothing into the cache and writes "
                    + "nothing, so it is safe to fire at any game key or season. Vary one input "
                    + "at a time to tell a refused season from a refused game from a dead token.")
    @ApiResponse(responseCode = "200", description = "What Yahoo answered, refusal included")
    @GetMapping("/probe")
    public YahooProbeResponse probe(
            @RequestParam(defaultValue = "nhl") String gameKey,
            @RequestParam(required = false) String season) {
        return probeService.probe(gameKey, season);
    }
}
