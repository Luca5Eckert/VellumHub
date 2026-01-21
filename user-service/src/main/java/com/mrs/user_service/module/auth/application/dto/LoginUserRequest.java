package com.mrs.user_service.module.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginUserRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
