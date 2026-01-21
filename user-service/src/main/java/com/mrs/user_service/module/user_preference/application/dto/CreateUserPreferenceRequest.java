package com.mrs.user_service.module.user_preference.application.dto;

import jakarta.validation.constraints.NotBlank;
import com.mrs.user_service.module.user.domain.Genre;

import java.util.List;

public record CreateUserPreferenceRequest(
        @NotBlank List<Genre> genres
) {
}
