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
    void parsesIdentityPositionsAndStats() throws Exception {
        when(oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID)).thenReturn("tok");
        when(client.getGamePlayers("tok", "nhl", 0, "2025")).thenReturn(json(
                "{\"fantasy_content\":{\"game\":[{\"game_key\":\"453\",\"code\":\"nhl\"},{\"players\":{"
                + "\"0\":{\"player\":[[{\"player_key\":\"453.p.1\"},{\"player_id\":\"1\"},"
                + "{\"name\":{\"full\":\"Connor McDavid\",\"first\":\"Connor\",\"last\":\"McDavid\"}},"
                + "{\"editorial_team_abbr\":\"Edm\"},{\"uniform_number\":\"97\"},"
                + "{\"display_position\":\"C,LW\"},{\"position_type\":\"P\"},"
                + "{\"image_url\":\"https://s.yimg.com/iu/api/res/1.2/HASH--~C/PARAMS--/"
                + "https://s.yimg.com/xe/i/us/sp/v/nhl_cutout/players_l/10132025/6743.png\"},"
                + "{\"eligible_positions\":[{\"position\":\"C\"},{\"position\":\"LW\"}]}],"
                + "{\"player_stats\":{\"stats\":[{\"stat\":{\"stat_id\":\"0\",\"value\":\"82\"}},"
                + "{\"stat\":{\"stat_id\":\"1\",\"value\":\"64\"}},{\"stat\":{\"stat_id\":\"2\",\"value\":\"89\"}},"
                + "{\"stat\":{\"stat_id\":\"15\",\"value\":\".184\"}},{\"stat\":{\"stat_id\":\"16\",\"value\":\"400\"}},"
                + "{\"stat\":{\"stat_id\":\"13\",\"value\":\"-\"}},{\"stat\":{\"stat_id\":\"34\",\"value\":\"22:00\"}}]}}]},"
                + "\"1\":{\"player\":[[{\"player_key\":\"453.p.2\"},{\"player_id\":\"2\"},"
                + "{\"name\":{\"full\":\"Igor Shesterkin\",\"first\":\"Igor\",\"last\":\"Shesterkin\"}},"
                + "{\"editorial_team_abbr\":\"NYR\"},{\"display_position\":\"G\"},{\"position_type\":\"G\"},"
                + "{\"eligible_positions\":[{\"position\":\"G\"}]}],"
                + "{\"player_stats\":{\"stats\":[{\"stat\":{\"stat_id\":\"0\",\"value\":\"58\"}},"
                + "{\"stat\":{\"stat_id\":\"19\",\"value\":\"36\"}},{\"stat\":{\"stat_id\":\"23\",\"value\":\"2.67\"}},"
                + "{\"stat\":{\"stat_id\":\"26\",\"value\":\".910\"}},{\"stat\":{\"stat_id\":\"27\",\"value\":\"3\"}}]}}]},"
                + "\"count\":2}}]}}"));

        List<YahooPlayerResponse> players = service().players("nhl", "2025");

        assertThat(players).hasSize(2);
        YahooPlayerResponse mcdavid = players.getFirst();
        assertThat(mcdavid.yahooId()).isEqualTo("1");
        assertThat(mcdavid.fullName()).isEqualTo("Connor McDavid");
        assertThat(mcdavid.firstName()).isEqualTo("Connor");
        assertThat(mcdavid.lastName()).isEqualTo("McDavid");
        assertThat(mcdavid.teamAbbrev()).isEqualTo("Edm");
        assertThat(mcdavid.position()).isEqualTo("C");
        assertThat(mcdavid.uniformNumber()).isEqualTo(97);
        assertThat(mcdavid.goalie()).isFalse();
        assertThat(mcdavid.eligiblePositions()).containsExactly("C", "LW");
        assertThat(mcdavid.imageUrl())
                .isEqualTo("https://s.yimg.com/xe/i/us/sp/v/nhl_cutout/players_l/10132025/6743.png");
        assertThat(mcdavid.goalieStats()).isNull();
        assertThat(mcdavid.skaterStats().gamesPlayed()).isEqualTo(82);
        assertThat(mcdavid.skaterStats().goals()).isEqualTo(64);
        assertThat(mcdavid.skaterStats().assists()).isEqualTo(89);
        assertThat(mcdavid.skaterStats().shootingPct()).isEqualTo(0.184);
        assertThat(mcdavid.skaterStats().faceoffsWon()).isEqualTo(400);
        assertThat(mcdavid.skaterStats().avgTimeOnIce()).isEqualTo("22:00");

        YahooPlayerResponse shesterkin = players.get(1);
        assertThat(shesterkin.goalie()).isTrue();
        assertThat(shesterkin.position()).isEqualTo("G");
        assertThat(shesterkin.eligiblePositions()).containsExactly("G");
        assertThat(shesterkin.skaterStats()).isNull();
        assertThat(shesterkin.goalieStats().gamesPlayed()).isEqualTo(58);
        assertThat(shesterkin.goalieStats().wins()).isEqualTo(36);
        assertThat(shesterkin.goalieStats().goalsAgainstAvg()).isEqualTo(2.67);
        assertThat(shesterkin.goalieStats().savePct()).isEqualTo(0.910);
        assertThat(shesterkin.goalieStats().shutouts()).isEqualTo(3);
    }

    @Test
    void paginatesUntilAPartialPageAndStops() throws Exception {
        when(oauthService.validAccessToken(YahooOAuthService.SERVICE_ACCOUNT_ID)).thenReturn("tok");
        when(client.getGamePlayers("tok", "nhl", 0, "2025")).thenReturn(page(25, 1));
        when(client.getGamePlayers("tok", "nhl", 25, "2025")).thenReturn(page(3, 26));

        List<YahooPlayerResponse> players = service().players("nhl", "2025");

        assertThat(players).hasSize(28);
        verify(client, never()).getGamePlayers("tok", "nhl", 50, "2025");
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
                    .append("{\"position_type\":\"P\"},")
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
