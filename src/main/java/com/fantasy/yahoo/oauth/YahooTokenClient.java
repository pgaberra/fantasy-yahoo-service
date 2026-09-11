package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import com.fantasy.yahoo.exception.YahooUpstreamException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.client.RestClientResponseException;

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

    /** Yahoo's name for a grant it will never honour again. */
    private static final String INVALID_GRANT = "invalid_grant";

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
        } catch (RestClientResponseException e) {
            // A refused grant is not an outage: it is permanent, and the only fix is re-consent.
            // Telling the two apart here is what lets the caller drop a dead token instead of
            // retrying it forever. Checked before RestClientException, which is its supertype.
            if (isInvalidGrant(e.getResponseBodyAsString())) {
                throw new YahooGrantRejectedException(
                        "Yahoo rejected the grant: " + e.getMessage(), e);
            }
            throw new YahooUpstreamException("Yahoo token request failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new YahooUpstreamException("Yahoo token request failed: " + e.getMessage(), e);
        }
        if (body == null || body.isBlank()) {
            throw new YahooUpstreamException("Yahoo token endpoint returned an empty body");
        }
        try {
            return objectMapper.readValue(body, TokenResponse.class);
        } catch (Exception e) {
            throw new YahooUpstreamException("Yahoo token endpoint returned unparseable JSON", e);
        }
    }

    /**
     * Reads the {@code error} field rather than looking for the word anywhere in the response, so
     * an unrelated failure that happens to quote it is not mistaken for a dead grant. An
     * unparseable body is not one either — that is a malformed answer, not a verdict.
     */
    private boolean isInvalidGrant(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            return INVALID_GRANT.equals(error.asText());
        } catch (Exception e) {
            return false;
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
