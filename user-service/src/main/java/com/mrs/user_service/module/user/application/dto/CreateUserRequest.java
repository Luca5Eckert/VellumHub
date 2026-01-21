package com.mrs.user_service.module.user.application.dto;

import com.mrs.user_service.module.user.domain.RoleUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotNull RoleUser role
) {
}
