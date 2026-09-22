package com.fantasy.yahoo.players;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.league.YahooFantasyClient.Attempt;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.players.dto.YahooLeagueProbeResponse;
import com.fantasy.yahoo.players.dto.YahooProbeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        return new YahooProbeService(oauthService, client).probe(gameKey, season, null, null);
    }

    private YahooProbeResponse probeLeague(String leagueKey) {
        return new YahooProbeService(oauthService, client).probe("nhl", null, leagueKey, null);
    }

    private YahooProbeResponse probeLeaguesListing() {
        return new YahooProbeService(oauthService, client).probe("nhl", null, null, "leagues");
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

    /**
     * The whole point of asking a league instead: the granted scope is about the user's own
     * leagues, so a league may be served where a whole game's collection is refused. If the two
     * answers differ, that difference is the diagnosis — so a league key must actually change
     * which call goes out.
     */
    @Test
    void asksTheLeaguesPlayersWhenGivenALeagueKey() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptLeaguePlayers(eq("token"), eq("465.l.12345"))).thenReturn(
                new Attempt("/league/465.l.12345/players", 200, ONE_PLAYER_PAGE, null));

        YahooProbeResponse response = probeLeague("465.l.12345");

        assertThat(response.ok()).isTrue();
        assertThat(response.players()).isEqualTo(1);
        assertThat(response.path()).contains("465.l.12345");
        verify(client, never()).attemptGamePlayers(any(), any(), any());
    }

    /**
     * The floor. If the account cannot even list its own leagues — the most basic thing the
     * granted scope covers — then no route into the Fantasy API is open, and the question stops
     * being which endpoint to use.
     */
    @Test
    void asksWhetherTheAccountCanListItsOwnLeagues() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptUserLeagues(eq("token"))).thenReturn(
                new Attempt("/users;use_login=1/games;game_keys=nhl/leagues", 403, FORBIDDEN_BODY,
                        "Forbidden"));

        YahooProbeResponse response = probeLeaguesListing();

        assertThat(response.ok()).isFalse();
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.error()).isEqualTo("This application is not authorized to perform this action.");
        verify(client, never()).attemptGamePlayers(any(), any(), any());
    }

    /** A leagues page carries no players, so counting them would read as an empty game. */
    @Test
    void countsNoPlayersOnALeaguesListing() {
        when(oauthService.validAccessToken(any())).thenReturn("token");
        when(client.attemptUserLeagues(any())).thenReturn(
                new Attempt("/users;use_login=1/games;game_keys=nhl/leagues", 200,
                        "{\"fantasy_content\":{\"users\":{}}}", null));

        YahooProbeResponse response = probeLeaguesListing();

        assertThat(response.ok()).isTrue();
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.players()).isNull();
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

    @Test
    void readsALeagueResourceRawWithTheServiceAccount() {
        when(oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID)).thenReturn("token");
        when(client.attemptLeagueResource("token", "477.l.1", "teams")).thenReturn(
                new Attempt("/league/477.l.1/teams", 200, "{\"fantasy_content\":{}}", null));

        YahooLeagueProbeResponse response =
                new YahooProbeService(oauthService, client).probeLeague("477.l.1", "teams", null);

        assertThat(response.ok()).isTrue();
        assertThat(response.body()).isEqualTo("{\"fantasy_content\":{}}");
    }

    @Test
    void readsALeagueResourceRawWithAGivenUsersToken() {
        when(oauthService.validAccessToken("user-7")).thenReturn("user-token");
        when(client.attemptLeagueResource("user-token", "477.l.124453", "draft")).thenReturn(
                new Attempt("/league/477.l.124453;out=settings,draftresults,teams", 200, "{\"a\":1}", null));

        YahooLeagueProbeResponse response = new YahooProbeService(oauthService, client)
                .probeLeague("477.l.124453", "draft", "user-7");

        assertThat(response.ok()).isTrue();
        assertThat(response.body()).isEqualTo("{\"a\":1}");
        verify(oauthService, never()).validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
    }

    @Test
    void saysWhoseTokenWasMissingWhenReadingAsAUser() {
        when(oauthService.validAccessToken("user-7")).thenThrow(new IllegalStateException("not connected"));

        YahooLeagueProbeResponse response = new YahooProbeService(oauthService, client)
                .probeLeague("477.l.1", "teams", "user-7");

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).startsWith("No usable token for that user:");
    }

    @Test
    void refusesAResourceOutsideTheListWithoutCallingYahoo() {
        YahooLeagueProbeResponse response = new YahooProbeService(oauthService, client)
                .probeLeague("477.l.1", "players;out=../x", null);

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).startsWith("resource must be one of");
        verify(client, never()).attemptLeagueResource(any(), any(), any());
    }
}
