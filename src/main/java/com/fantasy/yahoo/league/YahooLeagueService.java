package com.fantasy.yahoo.league;

import com.fantasy.yahoo.league.dto.DraftStatus;
import com.fantasy.yahoo.league.dto.LeagueDraftPick;
import com.fantasy.yahoo.league.dto.LeagueDraftResponse;
import com.fantasy.yahoo.league.dto.LeagueDraftTeam;
import com.fantasy.yahoo.league.dto.LeagueSettingsResponse;
import com.fantasy.yahoo.league.dto.LeagueSummary;
import com.fantasy.yahoo.league.dto.LeagueTeam;
import com.fantasy.yahoo.league.dto.LeagueTeamsResponse;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.league.dto.RosterSlot;
import com.fantasy.yahoo.league.dto.StatCategory;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    /**
     * The league's teams in draft order, which a draft setup needs; Yahoo's {@code /teams} lists
     * them by team id. The order comes from the draft results where Yahoo lists the slots, and from
     * each team's {@code draft_position} where it does not yet (see {@link #inDraftOrder}).
     */
    public LeagueTeamsResponse teams(String appUserId, String leagueKey) {
        JsonNode root = client.getLeagueDraft(oauthService.validAccessToken(appUserId), leagueKey);
        JsonNode leagueArray = root.path("fantasy_content").path("league");
        List<LeagueDraftPick> picks = parseDraftPicks(subresource(leagueArray, "draft_results"));
        List<LeagueTeam> teams = inDraftOrder(parseDraftTeams(subresource(leagueArray, "teams")), picks)
                .stream()
                .map(team -> new LeagueTeam(team.name(), team.mine()))
                .toList();
        return new LeagueTeamsResponse(teams);
    }

    public LeagueDraftResponse draft(String appUserId, String leagueKey) {
        JsonNode root = client.getLeagueDraft(oauthService.validAccessToken(appUserId), leagueKey);
        JsonNode leagueArray = root.path("fantasy_content").path("league");
        JsonNode meta = leagueArray.path(0);

        List<LeagueDraftPick> picks = parseDraftPicks(subresource(leagueArray, "draft_results"));
        List<LeagueDraftTeam> teams = inDraftOrder(parseDraftTeams(subresource(leagueArray, "teams")), picks);
        JsonNode settings = subresource(leagueArray, "settings").path(0);

        return new LeagueDraftResponse(
                firstNonBlank(text(meta, "league_key"), leagueKey),
                draftStatus(text(meta, "draft_status")),
                settings.path("is_auction_draft").asInt(0) == 1,
                teams,
                picks);
    }

    /** With {@code out=…} Yahoo lists each sub-resource as its own element after the metadata. */
    private static JsonNode subresource(JsonNode leagueArray, String name) {
        for (JsonNode element : leagueArray) {
            if (element.has(name)) {
                return element.get(name);
            }
        }
        return MissingNode.getInstance();
    }

    static DraftStatus draftStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return DraftStatus.UNKNOWN;
        }
        return switch (raw) {
            case "predraft" -> DraftStatus.PRE_DRAFT;
            case "postdraft" -> DraftStatus.FINISHED;
            default -> DraftStatus.IN_PROGRESS;
        };
    }

    private static List<LeagueDraftPick> parseDraftPicks(JsonNode draftResults) {
        List<LeagueDraftPick> picks = new ArrayList<>();
        for (JsonNode entry : numericChildren(draftResults)) {
            JsonNode result = flatten(entry.path("draft_result"));
            if (!result.hasNonNull("pick") || !result.hasNonNull("team_key")) {
                continue;
            }
            String playerKey = text(result, "player_key");
            picks.add(new LeagueDraftPick(
                    result.path("pick").asInt(),
                    result.path("round").asInt(0),
                    text(result, "team_key"),
                    playerKey == null || playerKey.isBlank() ? null : playerKey,
                    playerId(playerKey)));
        }
        picks.sort(Comparator.comparingInt(LeagueDraftPick::pick));
        return picks;
    }

    /** A team as Yahoo lists it, with its seat in the draft order when the commissioner has set one. */
    private record ParsedTeam(LeagueDraftTeam team, Integer draftPosition) {
    }

    private static List<ParsedTeam> parseDraftTeams(JsonNode teamsNode) {
        List<ParsedTeam> teams = new ArrayList<>();
        for (JsonNode entry : numericChildren(teamsNode)) {
            String teamKey = null;
            String name = null;
            boolean mine = false;
            Integer draftPosition = null;
            for (JsonNode attribute : entry.path("team").path(0)) {
                if (attribute.hasNonNull("team_key")) {
                    teamKey = attribute.get("team_key").asText();
                }
                if (attribute.hasNonNull("name")) {
                    name = attribute.get("name").asText();
                }
                if (attribute.hasNonNull("is_owned_by_current_login")) {
                    mine = attribute.get("is_owned_by_current_login").asInt(0) == 1;
                }
                if (attribute.hasNonNull("draft_position")) {
                    int position = attribute.get("draft_position").asInt(0);
                    draftPosition = position > 0 ? position : null;
                }
            }
            if (teamKey != null && name != null) {
                teams.add(new ParsedTeam(new LeagueDraftTeam(teamKey, name, mine), draftPosition));
            }
        }
        return teams;
    }

    /**
     * Teams in the order they pick in the first round. Yahoo lists the draft's slots only once the
     * draft is under way in some leagues, so before that each team's {@code draft_position} gives
     * the order the commissioner set. Any team placed by neither follows in Yahoo's order.
     */
    private static List<LeagueDraftTeam> inDraftOrder(List<ParsedTeam> teams, List<LeagueDraftPick> picks) {
        Map<String, ParsedTeam> remaining = new LinkedHashMap<>();
        teams.forEach(parsed -> remaining.put(parsed.team().teamKey(), parsed));
        List<LeagueDraftTeam> ordered = new ArrayList<>();
        for (LeagueDraftPick pick : picks) {
            if (pick.round() != 1) {
                continue;
            }
            ParsedTeam parsed = remaining.remove(pick.teamKey());
            if (parsed != null) {
                ordered.add(parsed.team());
            }
        }
        remaining.values().stream()
                .sorted(Comparator.comparing(ParsedTeam::draftPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ParsedTeam::team)
                .forEach(ordered::add);
        return ordered;
    }

    /** A draft result is an object, or an array of single-key objects; either way, one object. */
    private static JsonNode flatten(JsonNode node) {
        if (!node.isArray()) {
            return node;
        }
        ObjectNode merged =
                JsonNodeFactory.instance.objectNode();
        for (JsonNode part : node) {
            if (part.isObject()) {
                merged.setAll((ObjectNode) part);
            }
        }
        return merged;
    }

    static Integer playerId(String playerKey) {
        if (playerKey == null) {
            return null;
        }
        int marker = playerKey.lastIndexOf(".p.");
        if (marker < 0) {
            return null;
        }
        try {
            return Integer.valueOf(playerKey.substring(marker + 3));
        } catch (NumberFormatException e) {
            return null;
        }
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
                    displayOnly(stat, "is_only_display_stat"),
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

    private static boolean displayOnly(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return false;
        }
        JsonNode value = node.get(field);
        return value.asInt(0) == 1 || value.asBoolean();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
