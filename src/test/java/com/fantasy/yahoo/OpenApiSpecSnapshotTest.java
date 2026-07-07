package com.fantasy.yahoo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshot test that keeps the committed OpenAPI spec in lockstep with the code.
 *
 * On a normal build it fetches the live spec from the running app and asserts it
 * matches specs/openapi.yaml — so changing a controller/DTO without regenerating
 * the spec fails the build. fantasy-bff pins this spec to generate its client.
 * To regenerate after an intentional API change:
 *
 *     ./gradlew test -DupdateSpec=true
 *
 * then commit the updated specs/openapi.yaml.
 *
 * /v3/api-docs.yaml is behind the internal-key filter, so the request sends the test key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecSnapshotTest {

    @LocalServerPort
    private int port;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private static final Path SPEC = Path.of("specs", "openapi.yaml");

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = normalize(fetchSpec());

        if (Boolean.getBoolean("updateSpec")) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, generated);
            return;
        }

        assertThat(Files.exists(SPEC))
                .as("specs/openapi.yaml is missing — run ./gradlew test -DupdateSpec=true to generate it")
                .isTrue();

        String committed = normalize(Files.readString(SPEC));
        assertThat(generated)
                .as("specs/openapi.yaml is stale — run ./gradlew test -DupdateSpec=true and commit the result")
                .isEqualTo(committed);
    }

    private String fetchSpec() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs.yaml"))
                        .header("X-Internal-Api-Key", internalApiKey)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static String normalize(String raw) {
        return raw.replace("\r\n", "\n").strip() + "\n";
    }
}
