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
