package com.fantasy.yahoo.league;

import com.fantasy.yahoo.league.dto.LeagueSettingsResponse;
import com.fantasy.yahoo.league.dto.LeagueSummary;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.league.dto.RosterSlot;
import com.fantasy.yahoo.league.dto.StatCategory;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads a user's NHL fantasy leagues and a league's settings from Yahoo, parsing Yahoo's
 * deeply-nested JSON (numeric-keyed objects mixed into arrays) into clean DTOs for the BFF.
 */
@Service
public class YahooLeagueService {

    private final YahooOAuthService oauthService;
    private final YahooFantasyClient client;

    public YahooLeagueService(YahooOAuthService oauthService, YahooFantasyClient client) {
        this.oauthService = oauthService;
        this.client = client;
    }

    public LeaguesResponse leagues(String appUserId) {
        JsonNode root = client.getUserNhlLeagues(oauthService.validAccessToken(appUserId));
        // fantasy_content.users.0.user[1].games.0.game[1].leagues
        JsonNode leaguesNode = root.path("fantasy_content").path("users").path("0").path("user")
                .path(1).path("games").path("0").path("game").path(1).path("leagues");

        List<LeagueSummary> leagues = new ArrayList<>();
        for (JsonNode entry : numericChildren(leaguesNode)) {
            JsonNode league = entry.path("league").path(0);
            if (league.isMissingNode() || league.path("league_key").isMissingNode()) {
                continue;
            }
            leagues.add(new LeagueSummary(
                    text(league, "league_key"),
                    text(league, "name"),
                    intOrNull(league, "season"),
                    intOrNull(league, "num_teams"),
                    text(league, "scoring_type")));
        }
        return new LeaguesResponse(leagues);
    }

    public LeagueSettingsResponse settings(String appUserId, String leagueKey) {
        JsonNode root = client.getLeagueSettings(oauthService.validAccessToken(appUserId), leagueKey);
        JsonNode leagueArray = root.path("fantasy_content").path("league");
        JsonNode meta = leagueArray.path(0);
        JsonNode settings = leagueArray.path(1).path("settings").path(0);

        return new LeagueSettingsResponse(
                text(meta, "league_key"),
                text(meta, "name"),
                firstNonBlank(text(settings, "scoring_type"), text(meta, "scoring_type")),
                parseStatCategories(settings),
                parseRosterPositions(settings));
    }

    private static List<StatCategory> parseStatCategories(JsonNode settings) {
        // Points leagues carry a per-stat modifier; index it by stat_id to merge in.
        Map<Integer, Double> modifiers = new HashMap<>();
        for (JsonNode wrapper : settings.path("stat_modifiers").path("stats")) {
            JsonNode stat = wrapper.path("stat");
            if (stat.hasNonNull("stat_id")) {
                modifiers.put(stat.path("stat_id").asInt(), doubleOrNull(stat, "value"));
            }
        }
        List<StatCategory> categories = new ArrayList<>();
        for (JsonNode wrapper : settings.path("stat_categories").path("stats")) {
            JsonNode stat = wrapper.path("stat");
            if (stat.path("stat_id").isMissingNode()) {
                continue;
            }
            int statId = stat.path("stat_id").asInt();
            categories.add(new StatCategory(
                    statId,
                    text(stat, "name"),
                    text(stat, "display_name"),
                    text(stat, "position_type"),
                    modifiers.get(statId)));
        }
        return categories;
    }

    private static List<RosterSlot> parseRosterPositions(JsonNode settings) {
        List<RosterSlot> slots = new ArrayList<>();
        for (JsonNode wrapper : settings.path("roster_positions")) {
            JsonNode pos = wrapper.path("roster_position");
            if (pos.path("position").isMissingNode()) {
                continue;
            }
            slots.add(new RosterSlot(
                    text(pos, "position"),
                    pos.path("count").asInt(0),
                    text(pos, "position_type")));
        }
        return slots;
    }

    /** Children of a Yahoo numeric-keyed object ({@code {"0":…,"1":…,"count":N}}), in order. */
    private static List<JsonNode> numericChildren(JsonNode node) {
        List<JsonNode> children = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().chars().allMatch(Character::isDigit)) {
                children.add(field.getValue());
            }
        }
        return children;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        try {
            return Double.valueOf(node.get(field).asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
