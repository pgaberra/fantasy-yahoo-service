package com.fantasy.yahoo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only Yahoo failing is Yahoo's fault. A missing signing key or an undecryptable token used to
 * reach the BFF as a 502 logged as a Yahoo outage, because the advice read "upstream" into a JDK
 * exception type that this service also throws for its own faults.
 */
@WebMvcTest(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerUpstreamTest.ThrowingController.class)
class GlobalExceptionHandlerUpstreamTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void yahooFailing_isABadGateway() throws Exception {
        mockMvc.perform(get("/test/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Yahoo Fantasy API call failed"));
    }

    /** A refusal is a verdict, not an outage: it must not reach the BFF looking like one. */
    @Test
    void yahooRefusing_isAForbiddenWithYahoosWording() throws Exception {
        mockMvc.perform(get("/test/refused"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "Yahoo refused the request: This application is not authorized to perform this action."));
    }

    @Test
    void aLocalFault_isAnInternalError_notBlamedOnYahoo() throws Exception {
        mockMvc.perform(get("/test/local"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/upstream")
        String upstream() {
            throw new YahooUpstreamException("Yahoo Fantasy API call failed");
        }

        @GetMapping("/test/refused")
        String refused() {
            throw new YahooAccessDeniedException(
                    "Yahoo refused the request: This application is not authorized to perform this action.",
                    null);
        }

        @GetMapping("/test/local")
        String local() {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY is not configured");
        }
    }
}
