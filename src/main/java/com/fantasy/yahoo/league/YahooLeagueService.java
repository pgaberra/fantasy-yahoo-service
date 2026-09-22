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
     * The league's teams with the signed-in manager's own team at its draft position, which is all a
     * draft setup reads: the league's size and the manager's seat. Yahoo tells no other team's seat
     * before its draft starts, so the others stay in Yahoo's order.
     */
    public LeagueTeamsResponse teams(String appUserId, String leagueKey) {
        JsonNode root = client.getLeagueTeams(oauthService.validAccessToken(appUserId), leagueKey);
        JsonNode leagueArray = root.path("fantasy_content").path("league");
        List<LeagueTeam> teams = withOwnTeamAt(
                        parseDraftTeams(subresource(leagueArray, "teams")), ownDraftPosition(leagueArray.path(0)))
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
        List<LeagueDraftTeam> teams =
                inDraftOrder(parseDraftTeams(subresource(leagueArray, "teams")), picks, ownDraftPosition(meta));
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

    /** The signed-in manager's own seat in the draft order, from the league's metadata; null when unset. */
    private static Integer ownDraftPosition(JsonNode meta) {
        int position = meta.path("draft_position").asInt(0);
        return position > 0 ? position : null;
    }

    private static List<LeagueDraftTeam> parseDraftTeams(JsonNode teamsNode) {
        List<LeagueDraftTeam> teams = new ArrayList<>();
        for (JsonNode entry : numericChildren(teamsNode)) {
            String teamKey = null;
            String name = null;
            boolean mine = false;
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
            }
            if (teamKey != null && name != null) {
                teams.add(new LeagueDraftTeam(teamKey, name, mine));
            }
        }
        return teams;
    }

    /**
     * Teams in the order they pick in the first round, where Yahoo lists the draft's slots. Before a
     * draft starts it may list none, and then only the manager's own seat is known.
     */
    private static List<LeagueDraftTeam> inDraftOrder(
            List<LeagueDraftTeam> teams, List<LeagueDraftPick> picks, Integer ownDraftPosition) {
        Map<String, LeagueDraftTeam> remaining = new LinkedHashMap<>();
        teams.forEach(team -> remaining.put(team.teamKey(), team));
        List<LeagueDraftTeam> ordered = new ArrayList<>();
        for (LeagueDraftPick pick : picks) {
            LeagueDraftTeam team = pick.round() == 1 ? remaining.remove(pick.teamKey()) : null;
            if (team != null) {
                ordered.add(team);
            }
        }
        if (ordered.isEmpty()) {
            return withOwnTeamAt(teams, ownDraftPosition);
        }
        ordered.addAll(remaining.values());
        return ordered;
    }

    /** The teams with the manager's own moved to the given seat, the others keeping their order. */
    private static List<LeagueDraftTeam> withOwnTeamAt(List<LeagueDraftTeam> teams, Integer position) {
        LeagueDraftTeam own = teams.stream().filter(LeagueDraftTeam::mine).findFirst().orElse(null);
        if (own == null || position == null) {
            return teams;
        }
        List<LeagueDraftTeam> others = new ArrayList<>(teams);
        others.remove(own);
        others.add(Math.min(position, others.size() + 1) - 1, own);
        return others;
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
