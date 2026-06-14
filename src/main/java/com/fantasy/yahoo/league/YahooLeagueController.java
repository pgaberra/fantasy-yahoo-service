package com.fantasy.yahoo.league;

import com.fantasy.yahoo.league.dto.LeagueSettingsResponse;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Yahoo Leagues", description = "A user's Yahoo fantasy leagues and their settings")
@RestController
@RequestMapping("/api/v1/yahoo/leagues")
public class YahooLeagueController {

    private final YahooLeagueService leagueService;

    public YahooLeagueController(YahooLeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @Operation(summary = "List the user's NHL fantasy leagues")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leagues returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo")
    })
    @GetMapping
    public LeaguesResponse leagues(@RequestParam String appUserId) {
        return leagueService.leagues(appUserId);
    }

    @Operation(summary = "Get a league's scoring + roster settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings returned"),
            @ApiResponse(responseCode = "404", description = "User has not connected Yahoo")
    })
    @GetMapping("/{leagueKey}/settings")
    public LeagueSettingsResponse settings(@PathVariable String leagueKey,
                                           @RequestParam String appUserId) {
        return leagueService.settings(appUserId, leagueKey);
    }
}
