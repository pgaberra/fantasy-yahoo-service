package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import com.fantasy.yahoo.exception.YahooUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The distinction under test is the one that decides whether a stored token is thrown away: a
 * grant Yahoo has refused for good, versus a token endpoint that merely failed this time.
 */
class YahooTokenClientTest {

    private static final YahooOAuthProperties PROPS = new YahooOAuthProperties(
            "client-id", "client-secret", "https://yahoo.example/callback", "fspt-r",
            "state-signing-secret", "enc-key", "https://web.example/connect",
            "https://api.login.yahoo.example", "https://fantasy.example");

    private MockRestServiceServer server;
    private YahooTokenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.login.yahoo.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YahooTokenClient(builder.build(), PROPS);
    }

    @Test
    void refresh_whenYahooCallsTheGrantInvalid_isReportedAsARejectedGrant() {
        server.expect(requestTo(containsString("/oauth2/get_token")))
                .andRespond(withBadRequest()
                        .body("{\"error\":\"invalid_grant\","
                                + "\"error_description\":\"Invalid refresh token\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refresh("dead-token"))
                .isInstanceOf(YahooGrantRejectedException.class);

        server.verify();
    }

    @Test
    void refresh_whenYahooFailsForSomeOtherReason_staysAnUpstreamFailure() {
        // Retryable: the grant may well still be good, so the caller must keep the stored token.
        server.expect(requestTo(containsString("/oauth2/get_token")))
                .andRespond(withServerError()
                        .body("{\"error\":\"server_error\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refresh("good-token"))
                .isInstanceOf(YahooUpstreamException.class);
    }

    @Test
    void refresh_whenTheErrorBodyIsNotJson_staysAnUpstreamFailure() {
        // A malformed answer is a broken response, not a verdict on the grant — don't discard on it.
        server.expect(requestTo(containsString("/oauth2/get_token")))
                .andRespond(withBadRequest().body("<html>invalid_grant</html>")
                        .contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.refresh("good-token"))
                .isInstanceOf(YahooUpstreamException.class);
    }

    @Test
    void exchangeCode_whenYahooAnswersWithUnreadableJson_isAnUpstreamFailure() {
        server.expect(requestTo(containsString("/oauth2/get_token")))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchangeCode("the-code"))
                .isInstanceOf(YahooUpstreamException.class);
    }
}
