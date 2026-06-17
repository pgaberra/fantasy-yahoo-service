package com.fantasy.yahoo.player;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    /** Every player in the given Yahoo game (default "nhl") with their eligible positions. */
    public List<YahooPlayerResponse> players(String gameKey) {
        String accessToken = oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
        List<YahooPlayerResponse> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode root = client.getGamePlayers(accessToken, gameKey, page * PAGE_SIZE);
            List<YahooPlayerResponse> pagePlayers = parsePage(root);
            all.addAll(pagePlayers);
            if (pagePlayers.size() < PAGE_SIZE) {
                return all;
            }
        }
        log.warn("Yahoo player pagination hit the {}-page cap for game {}; result may be truncated",
                MAX_PAGES, gameKey);
        return all;
    }

    private static List<YahooPlayerResponse> parsePage(JsonNode root) {
        // fantasy_content.game[1].players is a numeric-keyed object of player entries.
        JsonNode playersNode = root.path("fantasy_content").path("game").path(1).path("players");
        List<YahooPlayerResponse> players = new ArrayList<>();
        for (JsonNode entry : numericChildren(playersNode)) {
            JsonNode attrs = entry.path("player").path(0);
            String yahooId = null;
            String fullName = null;
            String teamAbbrev = null;
            List<String> eligiblePositions = new ArrayList<>();
            for (JsonNode attr : attrs) {
                if (attr.hasNonNull("player_id")) {
                    yahooId = attr.get("player_id").asText();
                }
                if (attr.path("name").hasNonNull("full")) {
                    fullName = attr.path("name").path("full").asText();
                }
                if (attr.hasNonNull("editorial_team_abbr")) {
                    teamAbbrev = attr.get("editorial_team_abbr").asText();
                }
                for (JsonNode pos : attr.path("eligible_positions")) {
                    if (pos.hasNonNull("position")) {
                        eligiblePositions.add(pos.get("position").asText());
                    }
                }
            }
            if (yahooId != null && fullName != null) {
                players.add(new YahooPlayerResponse(yahooId, fullName, teamAbbrev, eligiblePositions));
            }
        }
        return players;
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
