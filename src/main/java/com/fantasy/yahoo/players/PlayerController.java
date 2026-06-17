package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.GoalieResponse;
import com.fantasy.yahoo.players.dto.SkaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Players", description = "Cached player read model (identity + Yahoo eligible positions + season stats)")
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Operation(summary = "All skaters with eligible positions and season stats")
    @ApiResponse(responseCode = "200", description = "Skaters returned")
    @GetMapping("/skaters")
    public List<SkaterResponse> skaters() {
        return playerService.getSkaters();
    }

    @Operation(summary = "All goalies with eligible positions and season stats")
    @ApiResponse(responseCode = "200", description = "Goalies returned")
    @GetMapping("/goalies")
    public List<GoalieResponse> goalies() {
        return playerService.getGoalies();
    }
}
