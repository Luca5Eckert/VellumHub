package com.vellumhub.user_service.module.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkGoogleIdentityRequest(
        @NotBlank String externalToken,
        @NotBlank String currentPassword
) {
}
