package com.fantasy.yahoo.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteLinkRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The app user claiming the connection: the signed-in user, or the service account.")
        @NotBlank @Size(max = 64)
        String appUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The one-time link code the OAuth callback handed to the browser.")
        @NotBlank @Size(max = 128)
        String code
) {
}
