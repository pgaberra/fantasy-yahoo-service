package com.fantasy.yahoo.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request Spring MVC itself turns away (a required parameter missing, a constrained one out of
 * range, a method the path does not serve) is the caller's mistake and answers with that 4xx. The
 * catch-all used to take these for faults: 500, and an ERROR log that reaches Sentry. The whole
 * context is booted because a {@code @Validated} controller is only validated through its proxy.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FrameworkClientErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void captureHandlerLog() {
        logged.start();
        handlerLog.addAppender(logged);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLog.detachAppender(logged);
    }

    @Test
    void missingRequiredParameter_onAValidatedController_isBadRequest() throws Exception {
        perform(get("/api/v1/yahoo/leagues/nhl.l.1/rosters"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void oversizedParameter_onAValidatedController_isBadRequest() throws Exception {
        perform(get("/api/v1/yahoo/leagues/nhl.l.1/rosters").param("appUserId", "x".repeat(129)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void missingRequiredParameter_onAControllerWithoutValidated_isBadRequest() throws Exception {
        perform(get("/api/v1/sync/probe/league").param("leagueKey", "nhl.l.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void oversizedParameter_onAControllerWithoutValidated_isBadRequest() throws Exception {
        String oversized = "x".repeat(65);
        perform(get("/api/v1/sync/probe/league").param("leagueKey", oversized).param("resource", "settings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void methodThePathDoesNotServe_isMethodNotAllowed_andKeepsItsAllowHeader() throws Exception {
        perform(post("/api/v1/sync/runs"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void acceptTheEndpointCannotMeet_isNotAcceptable_answeredAsJson() throws Exception {
        perform(get("/api/v1/sync/runs").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("Not Acceptable"));

        assertNothingLoggedAsAnError();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Internal-Api-Key", internalApiKey));
    }

    private void assertNothingLoggedAsAnError() {
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();
    }
}
