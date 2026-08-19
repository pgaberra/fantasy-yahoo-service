package com.fantasy.yahoo.oauth;

import com.fantasy.yahoo.config.YahooOAuthProperties;
import com.fantasy.yahoo.oauth.dto.AuthorizeUrlResponse;
import com.fantasy.yahoo.oauth.dto.ConnectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;

@Tag(name = "Yahoo OAuth", description = "Per-user Yahoo account connection flow")
@RestController
@RequestMapping("/api/v1/yahoo/oauth")
public class YahooOAuthController {

    private static final Logger log = LoggerFactory.getLogger(YahooOAuthController.class);

    private final YahooOAuthService oauthService;
    private final YahooOAuthProperties props;

    public YahooOAuthController(YahooOAuthService oauthService, YahooOAuthProperties props) {
        this.oauthService = oauthService;
        this.props = props;
    }

    @Operation(summary = "Build the Yahoo authorization URL",
            description = "Returns the Yahoo consent URL (with a signed state) the browser should be sent to.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Authorization URL created"))
    @PostMapping("/authorize-url")
    public AuthorizeUrlResponse authorizeUrl(@RequestParam String appUserId) {
        return new AuthorizeUrlResponse(oauthService.buildAuthorizeUrl(appUserId));
    }

    @Operation(summary = "Yahoo OAuth callback",
            description = "Yahoo redirects the browser here after consent. Exchanges the code for "
                    + "tokens, stores them, then redirects back to the web app. Public (validated by "
                    + "the signed state), so it is exempt from the internal API-key filter.")
    @ApiResponses(@ApiResponse(responseCode = "302", description = "Redirect back to the web app"))
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        Result result = connect(code, state, error);
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(props.webPostConnectUrl())
                .queryParam("yahoo", result.outcome() == Outcome.CONNECTED ? "connected" : "error");
        if (result.outcome() != Outcome.CONNECTED) {
            // Always one of our own fixed slugs -- nothing the request supplied is echoed back
            // into the redirect.
            target.queryParam("reason", result.outcome().slug());
            if (result.detail() != null) {
                target.queryParam("detail", result.detail());
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(target.build().toUri()).build();
    }

    /**
     * How a connect attempt ended. The web app needs to tell these apart: a declined consent is
     * someone changing their mind, an expired state is a slow round trip worth simply retrying,
     * and a failed exchange is Yahoo refusing us -- which is the one worth investigating.
     * Reporting all three as "error" is how a dead connection went unnoticed for two months.
     */
    private enum Outcome {
        CONNECTED("connected"),
        DECLINED("declined"),
        INVALID_STATE("invalid_state"),
        EXCHANGE_FAILED("exchange_failed");

        private final String slug;

        Outcome(String slug) {
            this.slug = slug;
        }

        String slug() {
            return slug;
        }
    }

    /**
     * The OAuth error codes Yahoo is allowed to send back (RFC 6749 §4.1.2.1). Anything outside
     * this set is dropped rather than passed on: the value comes from the request, and only a
     * known-good vocabulary may reach a redirect URL.
     *
     * <p>Worth carrying at all because these say very different things. {@code access_denied} is
     * someone pressing no; {@code invalid_scope} or {@code unauthorized_client} is Yahoo refusing
     * to let this app ask for Fantasy data in the first place, which is a problem no amount of
     * retrying fixes.
     */
    private static final Set<String> YAHOO_ERRORS = Set.of(
            "access_denied",
            "invalid_request",
            "invalid_scope",
            "server_error",
            "temporarily_unavailable",
            "unauthorized_client",
            "unsupported_response_type");

    /** A connect attempt's outcome, plus Yahoo's own word for it where we were given one. */
    private record Result(Outcome outcome, String detail) {
        static Result of(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    private Result connect(String code, String state, String error) {
        if (StringUtils.hasText(error) || !StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            // The user declined consent (Yahoo sends ?error=access_denied, no code) or the callback
            // arrived incomplete — an expected client outcome, not a server fault, so don't alert.
            log.info("Yahoo OAuth callback did not complete (error={})", sanitizeForLog(error));
            return new Result(Outcome.DECLINED, knownYahooError(error));
        }
        try {
            oauthService.handleCallback(code, state);
            return Result.of(Outcome.CONNECTED);
        } catch (IllegalArgumentException invalidState) {
            // Forged / expired / malformed state — a client error, and the usual shape of a bot
            // probing the public callback. Log at WARN so it can't flood ERROR alerting (Sentry).
            log.warn("Yahoo OAuth callback rejected an invalid state: {}",
                    sanitizeForLog(invalidState.getMessage()));
            return Result.of(Outcome.INVALID_STATE);
        } catch (RuntimeException fault) {
            // A genuine fault (token exchange failed, storage error) — worth an ERROR and an alert.
            log.error("Yahoo OAuth callback failed", fault);
            return Result.of(Outcome.EXCHANGE_FAILED);
        }
    }

    private static String knownYahooError(String error) {
        return error != null && YAHOO_ERRORS.contains(error) ? error : null;
    }

    // Strip CR/LF from a request-controlled value before logging it (defends against log forging).
    private static String sanitizeForLog(String value) {
        return value == null ? "null" : value.replace('\r', '_').replace('\n', '_');
    }

    @Operation(summary = "Yahoo connection status",
            description = "Whether the given user has a connected Yahoo account.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Connection status returned"))
    @GetMapping("/connection")
    public ConnectionResponse connection(@RequestParam String appUserId) {
        return new ConnectionResponse(oauthService.isConnected(appUserId));
    }
}
