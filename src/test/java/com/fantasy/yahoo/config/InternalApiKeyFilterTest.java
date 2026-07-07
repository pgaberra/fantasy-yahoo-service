package com.fantasy.yahoo.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.fantasy.yahoo.config.InternalApiKeyFilter.API_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalApiKeyFilterTest {

    private static final String TEST_KEY = "secret-key";

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter(TEST_KEY);
    }

    @Test
    void failsFast_whenKeyBlank() {
        InternalApiKeyFilter keyless = new InternalApiKeyFilter("");
        assertThatThrownBy(keyless::requireApiKey).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contextFailsToStart_whenKeyMissing() {
        new ApplicationContextRunner()
                .withBean(InternalApiKeyFilter.class, () -> new InternalApiKeyFilter(""))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldNotFilter_forActuatorHealthAndInfo() {
        assertThat(filter.shouldNotFilter(requestTo("/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/actuator/health/liveness"))).isTrue();
        assertThat(filter.shouldNotFilter(requestTo("/actuator/info"))).isTrue();
    }

    @Test
    void shouldNotFilter_forOAuthCallback() {
        assertThat(filter.shouldNotFilter(requestTo("/api/v1/yahoo/oauth/callback"))).isTrue();
    }

    @Test
    void shouldFilter_forProtectedApiPaths() {
        assertThat(filter.shouldNotFilter(requestTo("/api/v1/yahoo/leagues"))).isFalse();
    }

    @Test
    void shouldFilter_forActuatorTraversalIntoApi() {
        assertThat(filter.shouldNotFilter(requestTo("/actuator/health/../../api/v1/yahoo/leagues")))
                .isFalse();
        assertThat(filter.shouldNotFilter(
                requestTo("/actuator/health/%2e%2e/%2e%2e/api/v1/yahoo/leagues"))).isFalse();
        // matrix-parameter traversal that cleanPath alone doesn't collapse
        assertThat(filter.shouldNotFilter(requestTo("/actuator/health/..;/..;/api/v1/yahoo/leagues")))
                .isFalse();
    }

    @Test
    void rejects_malformedEncodedUri_withoutThrowing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/v1/yahoo/leagues%2"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejects_requestWithoutHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/v1/yahoo/leagues"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void rejects_requestWithWrongKey() throws Exception {
        MockHttpServletRequest request = requestTo("/api/v1/yahoo/leagues");
        request.addHeader(API_KEY_HEADER, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void allows_requestWithCorrectKey() throws Exception {
        MockHttpServletRequest request = requestTo("/api/v1/yahoo/leagues");
        request.addHeader(API_KEY_HEADER, TEST_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
