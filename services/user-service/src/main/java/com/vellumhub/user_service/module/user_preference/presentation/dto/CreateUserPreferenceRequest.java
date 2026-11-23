package com.vellumhub.user_service.module.user_preference.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(example = "{\"genres\":[\"Fantasy\",\"Science Fiction\"]}")
public record CreateUserPreferenceRequest(
        @NotEmpty @Size(max = 10) List<@NotBlank String> genres
) {}
