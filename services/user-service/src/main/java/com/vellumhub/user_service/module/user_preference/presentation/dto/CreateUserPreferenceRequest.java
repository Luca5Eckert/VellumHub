package com.vellumhub.user_service.module.user_preference.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import com.vellumhub.user_service.module.user.domain.Genre;

import java.util.List;

public record CreateUserPreferenceRequest(
        @NotBlank List<Genre> genres
) {
}
