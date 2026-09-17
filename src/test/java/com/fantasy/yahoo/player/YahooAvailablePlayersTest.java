package com.fantasy.yahoo.player;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.player.dto.YahooAvailability;
import com.fantasy.yahoo.player.dto.YahooAvailablePlayerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooAvailablePlayersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LEAGUE = "453.l.123";

    @Mock
    private YahooOAuthService oauthService;
    @Mock
    private YahooFantasyClient client;

    private YahooPlayerService service() {
        return new YahooPlayerService(oauthService, client);
    }

    @Test
    void readsTheUsersOwnTokenAndTellsFreeAgentsFromWaivers() throws Exception {
        when(oauthService.validAccessToken("user-1")).thenReturn("user-token");
        when(client.getAvailablePlayers("user-token", LEAGUE, 0, 25)).thenReturn(page(
                player("11", "Spencer Knight", "FLA", "G", "freeagents"),
                player("12", "Jack Roslovic", "Car", "C", "waivers")));

        List<YahooAvailablePlayerResponse> available = service().availablePlayers("user-1", LEAGUE, 25);

        assertThat(available).hasSize(2);
        YahooAvailablePlayerResponse knight = available.getFirst();
        assertThat(knight.yahooId()).isEqualTo("11");
        assertThat(knight.fullName()).isEqualTo("Spencer Knight");
        assertThat(knight.teamAbbrev()).isEqualTo("FLA");
        assertThat(knight.position()).isEqualTo("G");
        assertThat(knight.goalie()).isTrue();
        assertThat(knight.eligiblePositions()).containsExactly("G");
        assertThat(knight.availability()).isEqualTo(YahooAvailability.FREE_AGENT);
        assertThat(available.get(1).availability()).isEqualTo(YahooAvailability.WAIVERS);
        verify(oauthService, never()).validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID);
    }

    @Test
    void ownershipYahooDoesNotGiveIsUnknownRatherThanAGuess() throws Exception {
        when(oauthService.validAccessToken("user-1")).thenReturn("user-token");
        when(client.getAvailablePlayers(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(page(player("13", "Nobody Owned", "SJS", "LW", null)));

        assertThat(service().availablePlayers("user-1", LEAGUE, 25).getFirst().availability())
                .isEqualTo(YahooAvailability.UNKNOWN);
    }

    @Test
    void stopsAtTheLimitRatherThanPagingTheWholeWire() throws Exception {
        when(oauthService.validAccessToken("user-1")).thenReturn("user-token");
        // A full page, then the second call asks for only the five that are still wanted.
        when(client.getAvailablePlayers("user-token", LEAGUE, 0, 25)).thenReturn(fullPage(0));
        when(client.getAvailablePlayers("user-token", LEAGUE, 25, 5)).thenReturn(fullPage(25));

        assertThat(service().availablePlayers("user-1", LEAGUE, 30)).hasSize(30);
        verify(client, never()).getAvailablePlayers("user-token", LEAGUE, 50, 25);
    }

    @Test
    void aShortPageEndsTheRead() throws Exception {
        when(oauthService.validAccessToken("user-1")).thenReturn("user-token");
        when(client.getAvailablePlayers("user-token", LEAGUE, 0, 25))
                .thenReturn(page(player("21", "Last Man", "BUF", "RW", "freeagents")));

        assertThat(service().availablePlayers("user-1", LEAGUE, 150)).hasSize(1);
        verify(client, never()).getAvailablePlayers("user-token", LEAGUE, 25, 25);
    }

    private static JsonNode page(String... players) throws Exception {
        return MAPPER.readTree("{\"fantasy_content\":{\"league\":[{\"league_key\":\"" + LEAGUE
                + "\"},{\"players\":{" + numbered(players) + "\"count\":" + players.length + "}}]}}");
    }

    private static JsonNode fullPage(int from) throws Exception {
        String[] players = new String[25];
        for (int index = 0; index < 25; index++) {
            players[index] = player(String.valueOf(from + index), "Player " + (from + index),
                    "EDM", "C", "freeagents");
        }
        return page(players);
    }

    private static String numbered(String[] players) {
        StringBuilder json = new StringBuilder();
        for (int index = 0; index < players.length; index++) {
            json.append('"').append(index).append("\":").append(players[index]).append(',');
        }
        return json.toString();
    }

    private static String player(String id, String name, String team, String position, String ownership) {
        String ownershipNode = ownership == null
                ? ""
                : ",{\"ownership\":{\"ownership_type\":\"" + ownership + "\"}}";
        return "{\"player\":[[{\"player_id\":\"" + id + "\"},{\"name\":{\"full\":\"" + name + "\"}},"
                + "{\"editorial_team_abbr\":\"" + team + "\"},{\"display_position\":\"" + position + "\"},"
                + "{\"position_type\":\"" + ("G".equals(position) ? "G" : "P") + "\"},"
                + "{\"eligible_positions\":[{\"position\":\"" + position + "\"}]}]" + ownershipNode + "]}";
    }
}
