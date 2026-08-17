package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.league.YahooFantasyClient.Attempt;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The probe exists to make a refusal legible, so a refusal must come back as data. Anything that
 * throws instead — or that flattens Yahoo's own wording into a generic message — defeats the
 * whole point, which is telling a refused season from a refused game from a dead token.
 */
@ExtendWith(MockitoExtension.class)
class YahooProbeServiceTest {

    private static final String FORBIDDEN_BODY = """
            {"error":{"xml:lang":"en-us","yahoo:uri":"/fantasy/v2/game/nhl/players",
              "description":"This application is not authorized to perform this action.",
              "detail":""}}""";

    private static final String ONE_PLAYER_PAGE = """
            {"fantasy_content":{"game":[{"game_key":"465"},{"players":{
              "0":{"player":[[{"player_id":"1"}]]},"count":1}}]}}""";

    @Mock
    private YahooOAuthService oauthService;

    @Mock
    private YahooFantasyClient client;

    private YahooProbeResponse probe(String gameKey, String season) {
        return new YahooProbeService(oauthService, client).probe(gameKey, season);
    }

    @Test
    void reportsYahoosOwnWordingOnARefusal() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptGamePlayers(eq("token"), eq("nhl"), eq("2026"))).thenReturn(
                new Attempt("/game/nhl/players", 403, FORBIDDEN_BODY, "Forbidden"));

        YahooProbeResponse response = probe("nhl", "2026");

        assertThat(response.ok()).isFalse();
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.error()).isEqualTo("This application is not authorized to perform this action.");
    }

    @Test
    void reportsAUsablePageAndHowManyPlayersItCarried() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptGamePlayers(eq("token"), eq("453"), eq("2025"))).thenReturn(
                new Attempt("/game/453/players", 200, ONE_PLAYER_PAGE, null));

        YahooProbeResponse response = probe("453", "2025");

        assertThat(response.ok()).isTrue();
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.players()).isEqualTo(1);
        assertThat(response.error()).isNull();
    }

    /** The most common finding of all, and the one worth not dressing up as a server error. */
    @Test
    void reportsAMissingServiceAccountAsAFinding() {
        when(oauthService.validAccessToken(any()))
                .thenThrow(new IllegalStateException("Yahoo is not connected"));

        YahooProbeResponse response = probe("nhl", null);

        assertThat(response.ok()).isFalse();
        assertThat(response.status()).isNull();
        assertThat(response.error()).contains("No usable service-account token");
    }

    @Test
    void reportsAConnectionFailureWithoutAStatus() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptGamePlayers(any(), any(), any())).thenReturn(
                new Attempt("/game/nhl/players", null, null, "connection timed out"));

        YahooProbeResponse response = probe("nhl", null);

        assertThat(response.ok()).isFalse();
        assertThat(response.status()).isNull();
        assertThat(response.error()).isEqualTo("connection timed out");
    }

    /** A 200 that is not a player page is not a pass — it would read as "the game is empty". */
    @Test
    void refusesToCallAnUnreadableBodyASuccess() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptGamePlayers(any(), any(), any())).thenReturn(
                new Attempt("/game/nhl/players", 200, "{\"fantasy_content\":{}}", null));

        YahooProbeResponse response = probe("nhl", null);

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).contains("could not read as a player page");
    }
}
