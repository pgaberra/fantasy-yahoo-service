package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.league.YahooFantasyClient.Attempt;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.players.dto.YahooLeagueProbeResponse;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.TreeSet;

/**
 * Asks Yahoo one question and reports the answer verbatim: can the service account read this
 * game's players for this season?
 *
 * <p>It exists because that question took an afternoon to fail to answer. A sync that cannot
 * reach Yahoo records a failure, but a failure only tells you that *something* was refused — not
 * whether it was the season filter, the game, the token or the app. Distinguishing those means
 * varying one input at a time and reading the raw status, which until now needed the shared
 * internal API key and a shell. One call, no writes, nothing cached: it never touches the read
 * model, so it is safe to fire at anything.
 */
@Service
public class YahooProbeService {

    private static final Logger log = LoggerFactory.getLogger(YahooProbeService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The one value {@code target} takes. Matched exactly: a typo must not quietly ask
     * something else, which is the failure mode this whole tool exists to remove. */
    private static final String LEAGUES_TARGET = "leagues";

    private final YahooOAuthService oauthService;
    private final YahooFantasyClient client;

    public YahooProbeService(YahooOAuthService oauthService, YahooFantasyClient client) {
        this.oauthService = oauthService;
        this.client = client;
    }

    /**
     * @param gameKey Yahoo game to ask about — {@code nhl} for whichever season is current, or a
     *     numeric key to pin a past one. Ignored when a league key is given.
     * @param season season start year, or blank to send no season filter at all
     * @param leagueKey ask a league's player collection instead of the game's. The granted scope
     *     is about the user's own leagues, so this is the one that may be allowed when the other
     *     is not — and the difference between the two answers is the diagnosis.
     * @param target {@code leagues} asks whether the account can list its own leagues at all,
     *     ignoring the other two. That is the floor: if even this is refused, no route into the
     *     Fantasy API is open, and the question stops being which endpoint to use.
     */
    public YahooProbeResponse probe(String gameKey, String season, String leagueKey, String target) {
        String accessToken;
        try {
            accessToken = oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
        } catch (RuntimeException e) {
            // Not being connected is itself a finding, and the most common one — report it in the
            // same shape as everything else rather than as an error status on this endpoint.
            return new YahooProbeResponse(false, path(gameKey, season, leagueKey, target), null, null,
                    "No usable service-account token: " + e.getMessage());
        }

        Attempt attempt;
        if (LEAGUES_TARGET.equals(target)) {
            attempt = client.attemptUserLeagues(accessToken);
        } else if (hasText(leagueKey)) {
            attempt = client.attemptLeaguePlayers(accessToken, leagueKey);
        } else {
            attempt = client.attemptGamePlayers(accessToken, gameKey, season);
        }
        if (!attempt.ok()) {
            log.info("Yahoo probe for {} answered {}: {}", forLog(attempt.path()), attempt.status(),
                    forLog(attempt.body()));
            return new YahooProbeResponse(false, attempt.path(), attempt.status(), null,
                    describe(attempt));
        }
        if (LEAGUES_TARGET.equals(target)) {
            // A leagues page carries no players, so counting them would read as an empty game.
            return new YahooProbeResponse(true, attempt.path(), attempt.status(), null, null);
        }
        Integer players = countPlayers(attempt.body());
        if (players == null) {
            return new YahooProbeResponse(false, attempt.path(), attempt.status(), null,
                    "Yahoo answered 200 with a body this could not read as a player page");
        }
        return new YahooProbeResponse(true, attempt.path(), attempt.status(), players, null);
    }

    /**
     * One of a league's resources exactly as Yahoo sends it, read with the service account's token,
     * so only a league that account belongs to can be read.
     */
    public YahooLeagueProbeResponse probeLeague(String leagueKey, String resource) {
        if (!YahooFantasyClient.LEAGUE_RESOURCES.containsKey(resource)) {
            return new YahooLeagueProbeResponse(false, null, null, null,
                    "resource must be one of " + new TreeSet<>(YahooFantasyClient.LEAGUE_RESOURCES.keySet()));
        }
        String accessToken;
        try {
            accessToken = oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
        } catch (RuntimeException e) {
            return new YahooLeagueProbeResponse(false, null, null, null,
                    "No usable service-account token: " + e.getMessage());
        }
        Attempt attempt = client.attemptLeagueResource(accessToken, leagueKey, resource);
        if (!attempt.ok()) {
            return new YahooLeagueProbeResponse(false, attempt.path(), attempt.status(), null, describe(attempt));
        }
        return new YahooLeagueProbeResponse(true, attempt.path(), attempt.status(), attempt.body(), null);
    }

    /**
     * Yahoo's error bodies carry the useful sentence in {@code error.description} — "This
     * application is not authorized to perform this action" and the like. Fall back to the status
     * text when it is shaped differently.
     */
    private static String describe(Attempt attempt) {
        String described = YahooFantasyClient.errorDescription(attempt.body());
        return described != null ? described : attempt.error();
    }

    /** Null when the body is not a player page at all, which is a different answer from zero. */
    private static Integer countPlayers(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode players = MAPPER.readTree(body)
                    .path("fantasy_content").path("game").path(1).path("players");
            if (players.isMissingNode()) {
                return null;
            }
            int count = 0;
            var fields = players.fields();
            while (fields.hasNext()) {
                String key = fields.next().getKey();
                if (!key.isEmpty() && key.chars().allMatch(Character::isDigit)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return null;
        }
    }

    private static String forLog(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.replace("\r", " ").replace("\n", " ");
        return stripped.length() > 300 ? stripped.substring(0, 300) : stripped;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String path(String gameKey, String season, String leagueKey, String target) {
        if (LEAGUES_TARGET.equals(target)) {
            return "/users;use_login=1/games;game_keys=nhl/leagues";
        }
        if (hasText(leagueKey)) {
            return "/league/" + leagueKey + "/players";
        }
        return "/game/" + gameKey + "/players"
                + (hasText(season) ? " (season=" + season + ")" : "");
    }
}
