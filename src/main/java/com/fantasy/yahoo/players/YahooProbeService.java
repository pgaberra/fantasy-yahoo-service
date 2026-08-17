package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.league.YahooFantasyClient.Attempt;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final YahooOAuthService oauthService;
    private final YahooFantasyClient client;

    public YahooProbeService(YahooOAuthService oauthService, YahooFantasyClient client) {
        this.oauthService = oauthService;
        this.client = client;
    }

    /**
     * @param gameKey Yahoo game to ask about — {@code nhl} for whichever season is current, or a
     *     numeric key to pin a past one
     * @param season season start year, or blank to send no season filter at all
     */
    public YahooProbeResponse probe(String gameKey, String season) {
        String accessToken;
        try {
            accessToken = oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
        } catch (RuntimeException e) {
            // Not being connected is itself a finding, and the most common one — report it in the
            // same shape as everything else rather than as an error status on this endpoint.
            return new YahooProbeResponse(false, path(gameKey, season), null, null,
                    "No usable service-account token: " + e.getMessage());
        }

        Attempt attempt = client.attemptGamePlayers(accessToken, gameKey, season);
        if (!attempt.ok()) {
            log.info("Yahoo probe for {} answered {}: {}", forLog(attempt.path()), attempt.status(),
                    forLog(attempt.body()));
            return new YahooProbeResponse(false, attempt.path(), attempt.status(), null,
                    describe(attempt));
        }
        Integer players = countPlayers(attempt.body());
        if (players == null) {
            return new YahooProbeResponse(false, attempt.path(), attempt.status(), null,
                    "Yahoo answered 200 with a body this could not read as a player page");
        }
        return new YahooProbeResponse(true, attempt.path(), attempt.status(), players, null);
    }

    /**
     * Yahoo's error bodies carry the useful sentence in {@code error.description} — "This
     * application is not authorized to perform this action" and the like. Fall back to the status
     * text when it is shaped differently.
     */
    private static String describe(Attempt attempt) {
        String described = descriptionOf(attempt.body());
        return described != null ? described : attempt.error();
    }

    private static String descriptionOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode description = MAPPER.readTree(body).path("error").path("description");
            return description.isTextual() ? description.asText() : null;
        } catch (Exception e) {
            return null;
        }
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

    private static String path(String gameKey, String season) {
        return "/game/" + gameKey + "/players"
                + (season == null || season.isBlank() ? "" : " (season=" + season + ")");
    }
}
