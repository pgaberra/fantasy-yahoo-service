package com.fantasy.yahoo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class YahooRestClientConfig {

    /** RestClient for Yahoo's OAuth endpoints (api.login.yahoo.com): token exchange + refresh. */
    @Bean
    public RestClient yahooLoginRestClient(YahooOAuthProperties props) {
        return build(props.loginBaseUrl());
    }

    /** RestClient for the Yahoo Fantasy Sports API (league discovery + settings). */
    @Bean
    public RestClient yahooApiRestClient(YahooOAuthProperties props) {
        return build(props.apiBaseUrl());
    }

    /**
     * RestClient for the headshot images themselves, which sit on Yahoo's image CDN rather than
     * behind the API — so it carries no base URL and is called with the absolute source URL the
     * player feed gave us. The read timeout is the generous one: these are multi-megabyte
     * originals, fetched by the sync job where waiting costs nobody anything.
     */
    @Bean
    public RestClient headshotRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private static RestClient build(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
