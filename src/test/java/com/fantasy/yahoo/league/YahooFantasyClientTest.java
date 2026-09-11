package com.fantasy.yahoo.league;

import com.fantasy.yahoo.exception.YahooUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooFantasyClientTest {

    private MockRestServiceServer server;
    private YahooFantasyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://fantasy.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YahooFantasyClient(builder.build());
    }

    @Test
    void getLeagueSettings_putsTheLeagueKeyInThePathWithBearerToken() {
        server.expect(requestTo(containsString("/league/453.l.123/settings?format=json")))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getLeagueSettings("tok", "453.l.123");

        server.verify();
    }

    @Test
    void getLeagueSettings_urlEncodesTheLeagueKeySoItCannotInjectThePath() {
        // A '/' in the key must be percent-encoded (%2F) so it stays inside one path segment and
        // can't break out into a different Yahoo API path. Encoding is format-agnostic — no
        // assumption about Yahoo's key shape, so a future Yahoo format change won't 400 real keys.
        server.expect(requestTo(containsString("/league/453.l.1%2F..%2Fsecret/settings")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getLeagueSettings("tok", "453.l.1/../secret");

        server.verify();
    }

    @Test
    void getLeagueTeams_urlEncodesTheLeagueKey() {
        server.expect(requestTo(containsString("/league/453.l.1%2F..%2Fsecret/teams")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getLeagueTeams("tok", "453.l.1/../secret");

        server.verify();
    }

    @Test
    void aYahooErrorStatus_isAnUpstreamFailure() {
        server.expect(requestTo(containsString("/league/453.l.123/settings")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.getLeagueSettings("tok", "453.l.123"))
                .isInstanceOf(YahooUpstreamException.class);
    }

    @Test
    void anEmptyBody_isAnUpstreamFailure() {
        server.expect(requestTo(containsString("/league/453.l.123/settings")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getLeagueSettings("tok", "453.l.123"))
                .isInstanceOf(YahooUpstreamException.class);
    }
}
