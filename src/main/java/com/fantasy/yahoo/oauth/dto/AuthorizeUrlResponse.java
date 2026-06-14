package com.fantasy.yahoo.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthorizeUrlResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The Yahoo consent URL the browser should be redirected to.")
        String authorizeUrl
) {
}
