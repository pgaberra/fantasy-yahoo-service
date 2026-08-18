package com.fantasy.yahoo.player;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.player.dto.YahooGoalieStats;
import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import com.fantasy.yahoo.player.dto.YahooSkaterStats;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class YahooPlayerService {

    private static final Logger log = LoggerFactory.getLogger(YahooPlayerService.class);

    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGES = 80;

    private final YahooOAuthService oauthService;
    private final YahooFantasyClient client;

    public YahooPlayerService(YahooOAuthService oauthService, YahooFantasyClient client) {
        this.oauthService = oauthService;
        this.client = client;
    }

    /**
     * Every player in the given Yahoo game (default "nhl") with identity, headshot, eligible
     * positions and the requested season's stat line. A blank season means the current season.
     *
     * <p>Kept for the diagnostic probe. The sync reads through a league instead — see
     * {@link #leaguePlayers}: Yahoo refuses this collection, and the documented client offers no
     * game-wide player listing at all.
     */
    public List<YahooPlayerResponse> players(String gameKey, String season) {
        return paginate(
                (token, start) -> client.getGamePlayers(token, gameKey, start, season),
                root -> root.path("fantasy_content").path("game").path(1).path("players"),
                "game " + sanitize(gameKey));
    }

    /**
     * Every player in a league's collection — the same universe, reached the way the Fantasy API
     * is meant to be asked: through a league the account belongs to.
     */
    public List<YahooPlayerResponse> leaguePlayers(String leagueKey, String season) {
        return paginate(
                (token, start) -> client.getLeaguePlayers(token, leagueKey, start, season),
                root -> root.path("fantasy_content").path("league").path(1).path("players"),
                "league " + sanitize(leagueKey));
    }

    /** One page of players, by offset. */
    private interface Page {
        JsonNode fetch(String accessToken, int start);
    }

    private List<YahooPlayerResponse> paginate(
            Page page, java.util.function.Function<JsonNode, JsonNode> playersNode, String what) {
        String accessToken = oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
        List<YahooPlayerResponse> all = new ArrayList<>();
        for (int index = 0; index < MAX_PAGES; index++) {
            JsonNode root = page.fetch(accessToken, index * PAGE_SIZE);
            List<JsonNode> entries = numericChildren(playersNode.apply(root));
            for (JsonNode entry : entries) {
                YahooPlayerResponse player = parsePlayer(entry);
                if (player != null) {
                    all.add(player);
                }
            }
            if (entries.size() < PAGE_SIZE) {
                return all;
            }
        }
        log.warn("Yahoo player pagination hit the {}-page cap for {}; result may be truncated",
                MAX_PAGES, what);
        return all;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\r", "_").replace("\n", "_");
    }

    private static YahooPlayerResponse parsePlayer(JsonNode entry) {
        JsonNode playerArr = entry.path("player");
        JsonNode attrs = playerArr.path(0);
        String yahooId = null;
        String fullName = null;
        String firstName = null;
        String lastName = null;
        String teamAbbrev = null;
        String displayPosition = null;
        String positionType = null;
        String uniformNumber = null;
        String imageUrl = null;
        List<String> eligiblePositions = new ArrayList<>();
        for (JsonNode attr : attrs) {
            if (attr.hasNonNull("player_id")) {
                yahooId = attr.get("player_id").asText();
            }
            JsonNode name = attr.path("name");
            if (name.hasNonNull("full")) {
                fullName = name.get("full").asText();
            }
            if (name.hasNonNull("first")) {
                firstName = name.get("first").asText();
            }
            if (name.hasNonNull("last")) {
                lastName = name.get("last").asText();
            }
            if (attr.hasNonNull("editorial_team_abbr")) {
                teamAbbrev = attr.get("editorial_team_abbr").asText();
            }
            if (attr.hasNonNull("display_position")) {
                displayPosition = attr.get("display_position").asText();
            }
            if (attr.hasNonNull("position_type")) {
                positionType = attr.get("position_type").asText();
            }
            if (attr.hasNonNull("uniform_number")) {
                uniformNumber = attr.get("uniform_number").asText();
            }
            if (attr.hasNonNull("image_url")) {
                imageUrl = attr.get("image_url").asText();
            }
            for (JsonNode position : attr.path("eligible_positions")) {
                if (position.hasNonNull("position")) {
                    eligiblePositions.add(position.get("position").asText());
                }
            }
        }
        if (yahooId == null || fullName == null) {
            return null;
        }
        boolean goalie = "G".equals(positionType);
        Map<Integer, String> stats = parseStats(playerArr);
        return new YahooPlayerResponse(
                yahooId,
                fullName,
                blankToNull(firstName) != null ? firstName : firstNameOf(fullName),
                blankToNull(lastName) != null ? lastName : lastNameOf(fullName),
                teamAbbrev,
                primaryPosition(displayPosition, eligiblePositions, goalie),
                parseIntOrNull(uniformNumber),
                fullImage(imageUrl),
                goalie,
                eligiblePositions,
                goalie ? null : skaterStats(stats),
                goalie ? goalieStats(stats) : null);
    }

    /** Flattens Yahoo's {@code player_stats.stats} list into a stat_id → raw value map. */
    private static Map<Integer, String> parseStats(JsonNode playerArr) {
        Map<Integer, String> stats = new HashMap<>();
        for (int i = 1; i < playerArr.size(); i++) {
            JsonNode statsList = playerArr.path(i).path("player_stats").path("stats");
            if (!statsList.isArray()) {
                continue;
            }
            for (JsonNode entry : statsList) {
                JsonNode stat = entry.path("stat");
                if (stat.hasNonNull("stat_id")) {
                    stats.put(stat.get("stat_id").asInt(), stat.path("value").asText(null));
                }
            }
        }
        return stats;
    }

    private static YahooSkaterStats skaterStats(Map<Integer, String> stats) {
        return new YahooSkaterStats(
                intStat(stats, 0),
                intStat(stats, 1),
                intStat(stats, 2),
                intStat(stats, 3),
                intStat(stats, 4),
                intStat(stats, 5),
                intStat(stats, 6),
                intStat(stats, 8),
                intStat(stats, 9),
                intStat(stats, 11),
                intStat(stats, 12),
                intStat(stats, 14),
                doubleStat(stats, 15),
                intStat(stats, 16),
                intStat(stats, 17),
                intStat(stats, 31),
                intStat(stats, 32),
                strStat(stats, 34));
    }

    private static YahooGoalieStats goalieStats(Map<Integer, String> stats) {
        return new YahooGoalieStats(
                intStat(stats, 0),
                intStat(stats, 18),
                intStat(stats, 19),
                intStat(stats, 20),
                intStat(stats, 27),
                intStat(stats, 24),
                intStat(stats, 25),
                intStat(stats, 22),
                doubleStat(stats, 23),
                doubleStat(stats, 26));
    }

    private static String primaryPosition(String displayPosition, List<String> eligible, boolean goalie) {
        if (goalie) {
            return "G";
        }
        if (displayPosition != null && !displayPosition.isBlank()) {
            return displayPosition.split(",")[0].trim();
        }
        return eligible.isEmpty() ? "C" : eligible.getFirst();
    }

    /**
     * Yahoo's {@code image_url} is a resize-proxy URL that embeds the full-resolution source as
     * its last {@code https://…} segment; return that source for a crisp headshot.
     */
    private static String fullImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        int sourceStart = imageUrl.lastIndexOf("https://");
        return sourceStart > 0 ? imageUrl.substring(sourceStart) : imageUrl;
    }

    private static String firstNameOf(String fullName) {
        int split = fullName.lastIndexOf(' ');
        return split > 0 ? fullName.substring(0, split) : fullName;
    }

    private static String lastNameOf(String fullName) {
        int split = fullName.lastIndexOf(' ');
        return split > 0 ? fullName.substring(split + 1) : fullName;
    }

    private static Integer intStat(Map<Integer, String> stats, int statId) {
        String value = blankToNull(stats.get(statId));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleStat(Map<Integer, String> stats, int statId) {
        String value = blankToNull(stats.get(statId));
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String strStat(Map<Integer, String> stats, int statId) {
        return blankToNull(stats.get(statId));
    }

    private static Integer parseIntOrNull(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Treats null, blank, and Yahoo's "-" placeholder as absent. */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return (trimmed.isEmpty() || trimmed.equals("-")) ? null : trimmed;
    }

    private static List<JsonNode> numericChildren(JsonNode node) {
        List<JsonNode> children = new ArrayList<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getKey().isEmpty() && field.getKey().chars().allMatch(Character::isDigit)) {
                children.add(field.getValue());
            }
        }
        return children;
    }
}
