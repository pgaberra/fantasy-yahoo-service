package com.fantasy.yahoo.players;

import com.fantasy.yahoo.players.dto.GoalieResponse;
import com.fantasy.yahoo.players.dto.SkaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@Tag(name = "Players",
        description = "Cached player read model: the pool as it is now, with a chosen season's stats")
@RestController
@RequestMapping("/api/v1/players")
@Validated
public class PlayerController {

    private static final String SEASON_DESCRIPTION =
            "Season start year — 2025 is the 2025-26 season. Required: the season being collected "
                    + "and the season a caller wants to show are deliberately different for most "
                    + "of the year, so there is no sensible default. A player with no line for it "
                    + "comes back with no stats rather than being left out.";

    private static final String LIMIT_DESCRIPTION =
            "How many to return, highest scoring first (most wins, for goalies). Absent returns "
                    + "the whole pool in name order, which is what a caller ranking every player "
                    + "wants; a caller drawing a handful of rows should ask for a handful.";

    /** Above this a slice is most of the pool anyway, so the cap costs a real caller nothing. */
    private static final int MAX_LIMIT = 500;

    private static final Duration HEADSHOT_MAX_AGE = Duration.ofDays(7);

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Operation(summary = "Skaters with eligible positions and a season's stats",
            description = "The whole pool, or its highest scoring when `limit` is given.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Skaters returned"),
            @ApiResponse(responseCode = "400", description = "limit is not between 1 and 500")
    })
    @GetMapping("/skaters")
    public List<SkaterResponse> skaters(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season,
            @Parameter(description = LIMIT_DESCRIPTION)
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(MAX_LIMIT)
                    Integer limit) {
        return playerService.getSkaters(season, limit);
    }

    @Operation(summary = "Goalies with eligible positions and a season's stats",
            description = "The whole pool, or its winningest when `limit` is given.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goalies returned"),
            @ApiResponse(responseCode = "400", description = "limit is not between 1 and 500")
    })
    @GetMapping("/goalies")
    public List<GoalieResponse> goalies(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season,
            @Parameter(description = LIMIT_DESCRIPTION)
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(MAX_LIMIT)
                    Integer limit) {
        return playerService.getGoalies(season, limit);
    }

    @Operation(summary = "A player's headshot thumbnail",
            description = "The player's headshot scaled to a " + HeadshotThumbnailer.SIZE
                    + "px square PNG. Refreshed by the sync, so the response is safe to cache "
                    + "for a long time and is revalidated with an ETag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thumbnail returned"),
            @ApiResponse(responseCode = "404", description = "No headshot stored for this player")
    })
    @GetMapping(value = "/{playerId}/headshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> headshot(@PathVariable long playerId) {
        return playerService.getHeadshot(playerId)
                .map(headshot -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(HEADSHOT_MAX_AGE).cachePublic())
                        .eTag(Long.toHexString(headshot.updatedAt.toEpochMilli()))
                        .body(headshot.image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
