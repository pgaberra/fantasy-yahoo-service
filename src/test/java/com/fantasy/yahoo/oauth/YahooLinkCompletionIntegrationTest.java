package com.fantasy.yahoo.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The whole connect round trip against a real database, because the property that matters lives in
 * the transaction: a code claimed by the wrong user has to stay spent even though the claim fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "yahoo.oauth.login-base-url=https://login.example.test",
        "yahoo.oauth.web-post-connect-url=https://web.example.test"
})
class YahooLinkCompletionIntegrationTest {

    private static final String API_KEY = "test-internal-key";

    @LocalServerPort
    private int port;

    @Autowired
    private YahooOAuthService oauthService;

    @Autowired
    private YahooOAuthTokenRepository tokenRepository;

    @MockitoBean
    private YahooTokenClient tokenClient;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void aCodeClaimedByTheUserWhoStartedTheFlow_connectsThatUser_once() throws Exception {
        String linkCode = consentFinishedFor("starter-1");

        assertThat(tokenRepository.existsByAppUserId("starter-1")).isFalse();
        assertThat(complete("starter-1", linkCode).statusCode()).isEqualTo(200);
        assertThat(tokenRepository.existsByAppUserId("starter-1")).isTrue();

        assertThat(complete("starter-1", linkCode).statusCode()).isEqualTo(404);
    }

    @Test
    void aCodeClaimedBySomeoneElse_isAConflict_andIsGoneForEveryone() throws Exception {
        String linkCode = consentFinishedFor("starter-2");

        assertThat(complete("victim-2", linkCode).statusCode()).isEqualTo(409);
        assertThat(tokenRepository.existsByAppUserId("victim-2")).isFalse();

        assertThat(complete("starter-2", linkCode).statusCode()).isEqualTo(404);
        assertThat(tokenRepository.existsByAppUserId("starter-2")).isFalse();
    }

    @Test
    void anOversizedCode_isABadRequest() throws Exception {
        assertThat(complete("starter-3", "x".repeat(129)).statusCode()).isEqualTo(400);
    }

    @Test
    void completing_needsTheInternalApiKey() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/yahoo/oauth/complete"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"appUserId\":\"a\",\"code\":\"b\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private String consentFinishedFor(String appUserId) throws Exception {
        String state = UriComponentsBuilder.fromUriString(oauthService.buildAuthorizeUrl(appUserId))
                .build().getQueryParams().getFirst("state");
        when(tokenClient.exchangeCode("yahoo-code"))
                .thenReturn(new YahooTokenClient.TokenResponse("at", "rt", 3600, "bearer", "guid"));
        HttpResponse<String> callback = http.send(HttpRequest.newBuilder(
                        UriComponentsBuilder.fromUri(uri("/api/v1/yahoo/oauth/callback"))
                                .queryParam("code", "yahoo-code").queryParam("state", state).build().toUri())
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(callback.statusCode()).isEqualTo(302);
        URI location = URI.create(callback.headers().firstValue("Location").orElseThrow());
        assertThat(location.getHost()).isEqualTo("web.example.test");
        assertThat(location.getQuery()).isEqualTo("yahoo=confirm&account=user");
        assertThat(location.getFragment()).startsWith("link=");
        return location.getFragment().substring("link=".length());
    }

    private HttpResponse<String> complete(String appUserId, String code) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/v1/yahoo/oauth/complete"))
                        .header("Content-Type", "application/json")
                        .header("X-Internal-Api-Key", API_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"appUserId\":\"" + appUserId + "\",\"code\":\"" + code + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
