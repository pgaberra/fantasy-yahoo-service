package com.fantasy.yahoo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,info",
                "management.info.env.enabled=true",
                "info.app.name=fantasy-yahoo-service",
                "info.app.version=test-1.2.3"
        })
class ActuatorInfoTest {

    @LocalServerPort
    private int port;

    @Test
    void actuatorInfoExposesAppNameAndVersion() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/info")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"fantasy-yahoo-service\"");
        assertThat(response.body()).contains("\"version\":\"test-1.2.3\"");
    }
}
