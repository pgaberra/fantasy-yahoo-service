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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        boolean ok = true;
        try {
            oauthService.handleCallback(code, state);
        } catch (RuntimeException e) {
            // Don't surface a JSON error to the browser — redirect with a flag instead.
            log.error("Yahoo OAuth callback failed", e);
            ok = false;
        }
        URI target = UriComponentsBuilder.fromUriString(props.webPostConnectUrl())
                .queryParam("yahoo", ok ? "connected" : "error")
                .build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }

    @Operation(summary = "Yahoo connection status",
            description = "Whether the given user has a connected Yahoo account.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Connection status returned"))
    @GetMapping("/connection")
    public ConnectionResponse connection(@RequestParam String appUserId) {
        return new ConnectionResponse(oauthService.isConnected(appUserId));
    }
}
