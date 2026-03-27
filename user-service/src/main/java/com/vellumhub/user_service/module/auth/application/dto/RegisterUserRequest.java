package com.mrs.user_service.module.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
