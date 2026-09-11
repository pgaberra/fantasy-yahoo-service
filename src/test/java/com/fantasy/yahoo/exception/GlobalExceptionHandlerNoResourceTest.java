package com.fantasy.yahoo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goes through the dispatcher rather than calling the handler, because the thing that broke was
 * the routing: an unmapped path raised {@code NoResourceFoundException}, nothing but the catch-all
 * matched it, and the answer was a 500 with a stack trace at ERROR (JAVA-SPRING-BOOT-23, a BFF
 * calling /api/v1/sync/probe before this service's build had it).
 */
@WebMvcTest(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerNoResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aPathThisServiceDoesNotServe_isAQuiet404() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No resource found for the requested path"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
