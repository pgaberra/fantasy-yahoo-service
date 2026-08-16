package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.GoalieResponse;
import com.fantasy.yahoo.players.dto.SkaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Players",
        description = "Cached player read model: the pool as it is now, with a chosen season's stats")
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private static final String SEASON_DESCRIPTION =
            "Season start year — 2025 is the 2025-26 season. Required: the season being collected "
                    + "and the season a caller wants to show are deliberately different for most "
                    + "of the year, so there is no sensible default. A player with no line for it "
                    + "comes back with no stats rather than being left out.";

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Operation(summary = "All skaters with eligible positions and a season's stats")
    @ApiResponse(responseCode = "200", description = "Skaters returned")
    @GetMapping("/skaters")
    public List<SkaterResponse> skaters(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season) {
        return playerService.getSkaters(season);
    }

    @Operation(summary = "All goalies with eligible positions and a season's stats")
    @ApiResponse(responseCode = "200", description = "Goalies returned")
    @GetMapping("/goalies")
    public List<GoalieResponse> goalies(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season) {
        return playerService.getGoalies(season);
    }
}
