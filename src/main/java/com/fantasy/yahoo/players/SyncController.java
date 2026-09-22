package com.fantasy.yahoo.players;

import com.fantasy.yahoo.exception.ErrorDto;
import com.fantasy.yahoo.players.dto.SyncAcceptedResponse;
import com.fantasy.yahoo.players.dto.SyncRunResponse;
import com.fantasy.yahoo.players.dto.YahooLeagueProbeResponse;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import com.fantasy.yahoo.league.YahooLeagueService;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
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

    private final SyncService syncService;
    private final YahooProbeService probeService;
    private final YahooLeagueService leagueService;

    public SyncController(SyncService syncService, YahooProbeService probeService,
                          YahooLeagueService leagueService) {
        this.syncService = syncService;
        this.probeService = probeService;
        this.leagueService = leagueService;
    }

    @Operation(summary = "Trigger a player sync",
            description = "Refreshes the cached player read model from Yahoo. Runs asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Sync started"),
            @ApiResponse(responseCode = "409", description = "A sync is already running")
    })
    @PostMapping
    public ResponseEntity<SyncAcceptedResponse> triggerSync() {
        if (!syncService.startAsync()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new SyncAcceptedResponse("running"));
        }
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
                    + "at a time to tell a refused season from a refused game from a dead token. "
                    + "Give a leagueKey to ask a league's player collection instead of the whole "
                    + "game's — the granted scope is about leagues, so that one may be allowed "
                    + "where the other is not.")
    @ApiResponse(responseCode = "200", description = "What Yahoo answered, refusal included")
    @GetMapping("/probe")
    public YahooProbeResponse probe(
            @RequestParam(defaultValue = "nhl") String gameKey,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String leagueKey,
            @RequestParam(required = false) String target) {
        return probeService.probe(gameKey, season, leagueKey, target);
    }

    @Operation(summary = "Read one of a league's resources raw",
            description = "What Yahoo sends for a league's settings, teams, draftresults, or the three "
                    + "together (draft), verbatim, read with the service account's token, so only a "
                    + "league that account belongs to answers. For checking which fields Yahoo really "
                    + "fills before code relies on them.")
    @ApiResponse(responseCode = "200", description = "What Yahoo answered, refusal included")
    @GetMapping("/probe/league")
    public YahooLeagueProbeResponse probeLeague(
            @RequestParam @Size(max = 64) String leagueKey,
            @RequestParam @Size(max = 32) String resource) {
        return probeService.probeLeague(leagueKey, resource);
    }

    @Operation(summary = "The service account's own leagues",
            description = "The leagues the app-owned Yahoo account belongs to, with their keys. "
                    + "Two uses: it hands you a league key for the probe without hunting for one, "
                    + "and it is itself a test — if this succeeds while a game's player "
                    + "collection is refused, the account and its permission are fine and the "
                    + "refusal is about what was asked for, not who asked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leagues returned"),
            @ApiResponse(responseCode = "403",
                    description = "Yahoo refused the request; the message carries Yahoo's own wording",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/leagues")
    public LeaguesResponse serviceAccountLeagues() {
        return leagueService.leagues(YahooOAuthService.SERVICE_ACCOUNT_ID);
    }
}
