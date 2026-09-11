package com.fantasy.yahoo.league;

import com.fantasy.yahoo.exception.YahooUpstreamException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin wrapper over the Yahoo Fantasy Sports API. Returns raw Jackson {@link JsonNode}
 * trees because Yahoo's JSON is deeply nested with numeric-keyed objects mixed into
 * arrays — the {@link YahooLeagueService} parses defensively rather than binding to it.
 *
 * The body is fetched as a String and parsed with our own (Jackson 2) ObjectMapper:
 * Spring Boot 4's default converter is Jackson 3, which can't build a Jackson 2 JsonNode.
 */
@Component
public class YahooFantasyClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YahooFantasyClient(RestClient yahooApiRestClient) {
        this.restClient = yahooApiRestClient;
    }

    private static final String USER_LEAGUES_PATH =
            "/users;use_login=1/games;game_keys=nhl/leagues?format=json";

    /** The NHL leagues the authenticated user belongs to. */
    public JsonNode getUserNhlLeagues(String accessToken) {
        return get(accessToken, USER_LEAGUES_PATH);
    }

    /** A league's settings (scoring categories, roster positions, modifiers). */
    public JsonNode getLeagueSettings(String accessToken, String leagueKey) {
        // leagueKey is caller-supplied — pass it as a URI-template variable so RestClient
        // URL-encodes it into a single path segment (a stray '/', '?' etc. can't inject path or
        // query). Format-agnostic: no assumption about Yahoo's key shape.
        return get(accessToken, "/league/{leagueKey}/settings?format=json", leagueKey);
    }

    /** A league's teams (names, and which one belongs to the authenticated user). */
    public JsonNode getLeagueTeams(String accessToken, String leagueKey) {
        return get(accessToken, "/league/{leagueKey}/teams?format=json", leagueKey);
    }

    /**
     * One page (25) of a game's player collection with each player's season stat line,
     * starting at the given offset. When {@code season} is blank Yahoo uses the current season.
     */
    public JsonNode getGamePlayers(String accessToken, String gameKey, int start, String season) {
        return get(accessToken, gamePlayersPath(gameKey, start, season));
    }

    /**
     * One page of a *league's* player collection. Same shape as the game's, but reached through a
     * league the account belongs to — which is the only route the Fantasy API is documented to
     * offer, and the one that survives when the game-wide collection is refused.
     */
    public JsonNode getLeaguePlayers(String accessToken, String leagueKey, int start, String season) {
        String stats = (season == null || season.isBlank())
                ? "/stats;type=season"
                : "/stats;type=season;season=" + season;
        // The key is caller-supplied, so it goes in as a URI-template variable and is encoded
        // into a single path segment.
        return get(accessToken,
                "/league/{leagueKey}/players;start=" + start + ";count=25" + stats + "?format=json",
                leagueKey);
    }

    /**
     * The outcome of one call, with the failure as data rather than an exception.
     *
     * @param status Yahoo's HTTP status, or null when no response arrived at all
     * @param body the response body, or Yahoo's error body on a non-2xx
     * @param error what went wrong, or null on success
     */
    public record Attempt(String path, Integer status, String body, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    /**
     * Fetches a page of a game's players and reports what happened instead of throwing.
     *
     * <p>Every other call here turns a Yahoo failure into a {@link YahooUpstreamException}, which
     * is right when the caller needs the data — but useless when the failure *is* what you came
     * to look at. A 403 with Yahoo's own wording, against a game key and season you chose, is the
     * only thing that distinguishes "we are not allowed" from "that season is not there" from
     * "our token is dead". So this one hands the status back.
     */
    public Attempt attemptGamePlayers(String accessToken, String gameKey, String season) {
        String path = gamePlayersPath(gameKey, 0, season);
        return attempt(accessToken, path, path);
    }

    /**
     * The same question asked of a league rather than a whole game.
     *
     * <p>Worth having both: the granted Fantasy Sports scope talks about the user's own teams and
     * leagues, while a game's player collection belongs to nobody in particular. If one is refused
     * and the other is not, that difference is the answer.
     */
    public Attempt attemptLeaguePlayers(String accessToken, String leagueKey) {
        // The key is caller-supplied, so it goes in as a URI-template variable and gets encoded
        // into a single path segment — a stray '/' or '?' cannot reshape the request.
        return attempt(accessToken, leaguePlayersPath("{leagueKey}"),
                leaguePlayersPath(leagueKey), leagueKey);
    }

    /**
     * Whether the account can list its own leagues at all — the most basic thing the granted
     * scope covers, and so the one that separates "this call is not allowed" from "nothing is".
     */
    public Attempt attemptUserLeagues(String accessToken) {
        return attempt(accessToken, USER_LEAGUES_PATH, USER_LEAGUES_PATH);
    }

    private Attempt attempt(String accessToken, String uriTemplate, String display, Object... vars) {
        try {
            String body = restClient.get()
                    .uri(uriTemplate, vars)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return new Attempt(display, 200, body, null);
        } catch (RestClientResponseException e) {
            return new Attempt(display, e.getStatusCode().value(), e.getResponseBodyAsString(),
                    e.getStatusText());
        } catch (RestClientException e) {
            return new Attempt(display, null, null, e.getMessage());
        }
    }

    private static String leaguePlayersPath(String leagueKey) {
        return "/league/" + leagueKey + "/players;start=0;count=25/stats;type=season?format=json";
    }

    private static String gamePlayersPath(String gameKey, int start, String season) {
        String stats = (season == null || season.isBlank())
                ? "/stats;type=season"
                : "/stats;type=season;season=" + season;
        return "/game/" + gameKey + "/players;start=" + start + ";count=25" + stats + "?format=json";
    }

    private JsonNode get(String accessToken, String uriTemplate, Object... uriVariables) {
        String body;
        try {
            body = restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new YahooUpstreamException(
                    "Yahoo Fantasy API call failed for " + uriTemplate + ": " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new YahooUpstreamException("Yahoo Fantasy API returned an empty body for " + uriTemplate);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new YahooUpstreamException("Yahoo Fantasy API returned unparseable JSON for " + uriTemplate, e);
        }
    }
}
