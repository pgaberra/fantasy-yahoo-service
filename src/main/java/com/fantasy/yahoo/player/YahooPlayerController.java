package com.fantasy.yahoo.player;

import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Yahoo Players",
        description = "League-wide Yahoo player list with fantasy eligible positions "
                + "(uses the app-owned service account)")
@RestController
@RequestMapping("/api/v1/yahoo/players")
public class YahooPlayerController {

    private final YahooPlayerService playerService;

    public YahooPlayerController(YahooPlayerService playerService) {
        this.playerService = playerService;
    }

    @Operation(summary = "List every Yahoo player for a game with eligible positions and season stats",
            description = "Paginates the Yahoo game player collection using the service account "
                    + "token, returning each player's identity, headshot, eligible positions and the "
                    + "requested season's stat line. Defaults to the NHL game; a blank season means "
                    + "the current season.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Players returned"),
            @ApiResponse(responseCode = "404", description = "The Yahoo service account is not connected")
    })
    @GetMapping
    public List<YahooPlayerResponse> players(
            @RequestParam(defaultValue = "nhl") String gameKey,
            @RequestParam(required = false) String season) {
        return playerService.players(gameKey, season);
    }
}
