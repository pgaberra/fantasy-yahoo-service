package com.fantasy.yahoo.league;

import com.fantasy.yahoo.exception.ErrorDto;
import com.fantasy.yahoo.league.dto.LeagueDraftResponse;
import com.fantasy.yahoo.league.dto.LeagueRostersResponse;
import com.fantasy.yahoo.league.dto.LeagueSettingsResponse;
import com.fantasy.yahoo.league.dto.LeagueTeamsResponse;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.player.YahooPlayerService;
import com.fantasy.yahoo.player.dto.YahooAvailablePlayerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Tag(name = "Yahoo Leagues", description = "A user's Yahoo fantasy leagues and their settings")
@RestController
@Validated
@RequestMapping("/api/v1/yahoo/leagues")
public class YahooLeagueController {

    static final String REFUSED = "Yahoo refused the request; the message carries Yahoo's own wording";

    /** Twelve pages of Yahoo's actual-rank order, which is deeper than any league's waiver wire. */
    private static final int MAX_AVAILABLE = 300;

    private final YahooLeagueService leagueService;
    private final YahooPlayerService playerService;

    public YahooLeagueController(YahooLeagueService leagueService, YahooPlayerService playerService) {
        this.leagueService = leagueService;
        this.playerService = playerService;
    }

    @Operation(summary = "List the user's NHL fantasy leagues")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leagues returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping
    public LeaguesResponse leagues(@RequestParam String appUserId) {
        return leagueService.leagues(appUserId);
    }

    @Operation(summary = "Get a league's scoring + roster settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{leagueKey}/settings")
    public LeagueSettingsResponse settings(@PathVariable String leagueKey,
                                           @RequestParam String appUserId) {
        return leagueService.settings(appUserId, leagueKey);
    }

    @Operation(summary = "List a league's teams (names + which is the user's own)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teams returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{leagueKey}/teams")
    public LeagueTeamsResponse teams(@PathVariable String leagueKey,
                                     @RequestParam String appUserId) {
        return leagueService.teams(appUserId, leagueKey);
    }

    @Operation(summary = "Get a league's draft: status, teams in draft order, and the picks made so far")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{leagueKey}/draft")
    public LeagueDraftResponse draft(@PathVariable String leagueKey,
                                     @RequestParam String appUserId) {
        return leagueService.draft(appUserId, leagueKey);
    }

    @Operation(summary = "List a league's teams with the players each holds now",
            description = "Each team's current roster, after trades, drops and pickups, bench and injured "
                    + "reserve included. Player ids are the ids the draft's picks and the player read model use.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rosters returned"),
            @ApiResponse(responseCode = "400", description = "The league key or user id is blank or too long",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{leagueKey}/rosters")
    public LeagueRostersResponse rosters(@PathVariable @NotBlank @Size(max = 64) String leagueKey,
                                         @RequestParam @NotBlank @Size(max = 128) String appUserId) {
        return leagueService.rosters(appUserId, leagueKey);
    }

    @Operation(summary = "List the players the league has available",
            description = "Free agents and players on waivers, in Yahoo's actual-rank order, so the "
                    + "first rows are the best available. Read with the user's own Yahoo token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Available players returned"),
            @ApiResponse(responseCode = "400", description = "The limit is outside 1-" + MAX_AVAILABLE),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo"),
            @ApiResponse(responseCode = "403", description = REFUSED,
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{leagueKey}/free-agents")
    public List<YahooAvailablePlayerResponse> freeAgents(
            @PathVariable @NotBlank @Size(max = 64) String leagueKey,
            @RequestParam @NotBlank @Size(max = 128) String appUserId,
            @RequestParam(defaultValue = "150") @Min(1) @Max(MAX_AVAILABLE) int limit) {
        return playerService.availablePlayers(appUserId, leagueKey, limit);
    }
}
