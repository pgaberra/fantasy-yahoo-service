package com.fantasy.yahoo.player;

import com.fantasy.yahoo.league.YahooFantasyClient;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fantasy.yahoo.player.dto.YahooPlayerResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooPlayerServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private YahooOAuthService oauthService;
    @Mock
    private YahooFantasyClient client;

    private YahooPlayerService service() {
        return new YahooPlayerService(oauthService, client);
    }

    @Test
    void parsesPlayersWithEligiblePositions() throws Exception {
        when(oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID)).thenReturn("tok");
        when(client.getGamePlayers("tok", "nhl", 0)).thenReturn(json(
                "{\"fantasy_content\":{\"game\":[{\"game_key\":\"453\",\"code\":\"nhl\"},{\"players\":{"
                + "\"0\":{\"player\":[[{\"player_key\":\"453.p.1\"},{\"player_id\":\"1\"},"
                + "{\"name\":{\"full\":\"Connor McDavid\"}},{\"editorial_team_abbr\":\"Edm\"},"
                + "{\"display_position\":\"C\"},{\"position_type\":\"P\"},"
                + "{\"eligible_positions\":[{\"position\":\"C\"},{\"position\":\"LW\"}]}]]},"
                + "\"1\":{\"player\":[[{\"player_key\":\"453.p.2\"},{\"player_id\":\"2\"},"
                + "{\"name\":{\"full\":\"Igor Shesterkin\"}},{\"editorial_team_abbr\":\"NYR\"},"
                + "{\"display_position\":\"G\"},{\"position_type\":\"G\"},"
                + "{\"eligible_positions\":[{\"position\":\"G\"}]}]]},"
                + "\"count\":2}}]}}"));

        List<YahooPlayerResponse> players = service().players("nhl");

        assertThat(players).hasSize(2);
        YahooPlayerResponse mcdavid = players.getFirst();
        assertThat(mcdavid.yahooId()).isEqualTo("1");
        assertThat(mcdavid.fullName()).isEqualTo("Connor McDavid");
        assertThat(mcdavid.teamAbbrev()).isEqualTo("Edm");
        assertThat(mcdavid.eligiblePositions()).containsExactly("C", "LW");
        assertThat(players.get(1).eligiblePositions()).containsExactly("G");
    }

    @Test
    void paginatesUntilAPartialPageAndStops() throws Exception {
        when(oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID)).thenReturn("tok");
        when(client.getGamePlayers("tok", "nhl", 0)).thenReturn(page(25, 1));
        when(client.getGamePlayers("tok", "nhl", 25)).thenReturn(page(3, 26));

        List<YahooPlayerResponse> players = service().players("nhl");

        assertThat(players).hasSize(28);
        verify(client, never()).getGamePlayers("tok", "nhl", 50);
    }

    private static JsonNode page(int count, int firstId) throws Exception {
        StringBuilder sb = new StringBuilder(
                "{\"fantasy_content\":{\"game\":[{\"game_key\":\"453\",\"code\":\"nhl\"},{\"players\":{");
        for (int i = 0; i < count; i++) {
            int id = firstId + i;
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(i).append("\":{\"player\":[[")
                    .append("{\"player_id\":\"").append(id).append("\"},")
                    .append("{\"name\":{\"full\":\"Player ").append(id).append("\"}},")
                    .append("{\"editorial_team_abbr\":\"Edm\"},")
                    .append("{\"eligible_positions\":[{\"position\":\"C\"}]}")
                    .append("]]}");
        }
        sb.append(",\"count\":").append(count).append("}}]}}");
        return json(sb.toString());
    }

    private static JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }
}
