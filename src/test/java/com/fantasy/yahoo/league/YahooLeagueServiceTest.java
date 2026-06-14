package com.fantasy.yahoo.league;

import com.fantasy.yahoo.league.dto.LeagueSettingsResponse;
import com.fantasy.yahoo.league.dto.LeaguesResponse;
import com.fantasy.yahoo.oauth.YahooOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooLeagueServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER = "user-1";

    @Mock
    private YahooOAuthService oauthService;
    @Mock
    private YahooFantasyClient client;

    private YahooLeagueService service() {
        return new YahooLeagueService(oauthService, client);
    }

    @Test
    void leagues_parsesYahooNestedShape() throws Exception {
        when(oauthService.validAccessToken(USER)).thenReturn("token");
        when(client.getUserNhlLeagues("token")).thenReturn(json(
                "{\"fantasy_content\":{\"users\":{\"0\":{\"user\":[{\"guid\":\"X\"},{\"games\":{\"0\":"
                + "{\"game\":[{\"game_key\":\"453\",\"code\":\"nhl\"},{\"leagues\":{\"0\":{\"league\":"
                + "[{\"league_key\":\"453.l.123\",\"name\":\"My League\",\"season\":\"2025\","
                + "\"num_teams\":12,\"scoring_type\":\"head\"}]},\"count\":1}}]},\"count\":1}}]},"
                + "\"count\":1}}}"));

        LeaguesResponse response = service().leagues(USER);

        assertThat(response.leagues()).hasSize(1);
        assertThat(response.leagues().getFirst().leagueKey()).isEqualTo("453.l.123");
        assertThat(response.leagues().getFirst().name()).isEqualTo("My League");
        assertThat(response.leagues().getFirst().season()).isEqualTo(2025);
        assertThat(response.leagues().getFirst().numTeams()).isEqualTo(12);
        assertThat(response.leagues().getFirst().scoringType()).isEqualTo("head");
    }

    @Test
    void settings_parsesScoringAndRoster() throws Exception {
        when(oauthService.validAccessToken(USER)).thenReturn("token");
        when(client.getLeagueSettings("token", "453.l.123")).thenReturn(json(
                "{\"fantasy_content\":{\"league\":[{\"league_key\":\"453.l.123\",\"name\":\"My League\","
                + "\"scoring_type\":\"head\"},{\"settings\":[{\"scoring_type\":\"head\","
                + "\"stat_categories\":{\"stats\":["
                + "{\"stat\":{\"stat_id\":1,\"name\":\"Goals\",\"display_name\":\"G\",\"position_type\":\"P\"}},"
                + "{\"stat\":{\"stat_id\":2,\"name\":\"Assists\",\"display_name\":\"A\",\"position_type\":\"P\"}}]},"
                + "\"stat_modifiers\":{\"stats\":["
                + "{\"stat\":{\"stat_id\":1,\"value\":\"3\"}},{\"stat\":{\"stat_id\":2,\"value\":\"2\"}}]},"
                + "\"roster_positions\":["
                + "{\"roster_position\":{\"position\":\"C\",\"position_type\":\"P\",\"count\":2}},"
                + "{\"roster_position\":{\"position\":\"BN\",\"count\":4}}]}]}]}}"));

        LeagueSettingsResponse settings = service().settings(USER, "453.l.123");

        assertThat(settings.leagueKey()).isEqualTo("453.l.123");
        assertThat(settings.name()).isEqualTo("My League");
        assertThat(settings.scoringType()).isEqualTo("head");
        assertThat(settings.statCategories()).hasSize(2);
        assertThat(settings.statCategories().getFirst().name()).isEqualTo("Goals");
        assertThat(settings.statCategories().getFirst().pointValue()).isEqualTo(3.0);
        assertThat(settings.rosterPositions()).hasSize(2);
        assertThat(settings.rosterPositions().getFirst().position()).isEqualTo("C");
        assertThat(settings.rosterPositions().getFirst().count()).isEqualTo(2);
        assertThat(settings.rosterPositions().get(1).position()).isEqualTo("BN");
        assertThat(settings.rosterPositions().get(1).count()).isEqualTo(4);
    }

    private static JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }
}
