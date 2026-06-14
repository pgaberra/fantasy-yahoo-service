package com.fantasy.yahoo.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the user has a connected Yahoo account.")
        boolean connected
) {
}
