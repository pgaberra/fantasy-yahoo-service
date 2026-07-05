package com.fantasy.yahoo.league;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    /** The NHL leagues the authenticated user belongs to. */
    public JsonNode getUserNhlLeagues(String accessToken) {
        return get(accessToken, "/users;use_login=1/games;game_keys=nhl/leagues?format=json");
    }

    /** A league's settings (scoring categories, roster positions, modifiers). */
    public JsonNode getLeagueSettings(String accessToken, String leagueKey) {
        return get(accessToken, "/league/" + leagueKey + "/settings?format=json");
    }

    /** A league's teams (names, and which one belongs to the authenticated user). */
    public JsonNode getLeagueTeams(String accessToken, String leagueKey) {
        return get(accessToken, "/league/" + leagueKey + "/teams?format=json");
    }

    /**
     * One page (25) of a game's player collection with each player's season stat line,
     * starting at the given offset. When {@code season} is blank Yahoo uses the current season.
     */
    public JsonNode getGamePlayers(String accessToken, String gameKey, int start, String season) {
        String stats = (season == null || season.isBlank())
                ? "/stats;type=season"
                : "/stats;type=season;season=" + season;
        return get(accessToken,
                "/game/" + gameKey + "/players;start=" + start + ";count=25" + stats + "?format=json");
    }

    private JsonNode get(String accessToken, String path) {
        String body;
        try {
            body = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Yahoo Fantasy API call failed for " + path + ": " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Yahoo Fantasy API returned an empty body for " + path);
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Yahoo Fantasy API returned unparseable JSON for " + path, e);
        }
    }
}
