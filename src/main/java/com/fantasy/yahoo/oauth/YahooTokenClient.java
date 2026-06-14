package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Calls Yahoo's OAuth token endpoint to exchange an authorization code for tokens and
 * to refresh an expired access token. Client authentication uses HTTP Basic.
 *
 * The body is fetched as a String and parsed with our own (Jackson 2) ObjectMapper:
 * Spring Boot 4's default message converter is Jackson 3, which can't bind into a
 * Jackson 2 type. Yahoo replies with snake_case JSON.
 */
@Component
public class YahooTokenClient {

    private final RestClient restClient;
    private final YahooOAuthProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public YahooTokenClient(RestClient yahooLoginRestClient, YahooOAuthProperties props) {
        this.restClient = yahooLoginRestClient;
        this.props = props;
    }

    public TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", props.redirectUri());
        form.add("code", code);
        return post(form);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("redirect_uri", props.redirectUri());
        form.add("refresh_token", refreshToken);
        return post(form);
    }

    private TokenResponse post(MultiValueMap<String, String> form) {
        String body;
        try {
            body = restClient.post()
                    .uri("/oauth2/get_token")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Yahoo token request failed: " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Yahoo token endpoint returned an empty body");
        }
        try {
            return objectMapper.readValue(body, TokenResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Yahoo token endpoint returned unparseable JSON", e);
        }
    }

    private String basicAuth() {
        String creds = props.clientId() + ":" + props.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType,
            String xoauthYahooGuid
    ) {
        public Instant expiresAt(Instant now) {
            return now.plusSeconds(expiresIn);
        }
    }
}
