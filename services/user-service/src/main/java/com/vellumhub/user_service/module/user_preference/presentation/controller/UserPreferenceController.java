package com.vellumhub.user_service.module.user_preference.presentation.controller;

import com.vellumhub.kafka.contracts.user.CatalogGenreNames;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.application.use_case.CreateUserPreferenceUseCase;
import com.vellumhub.user_service.module.user_preference.domain.exception.UserPreferenceDomainException;
import com.vellumhub.user_service.module.user_preference.presentation.dto.CreateUserPreferenceRequest;
import com.vellumhub.user_service.share.exception.ApiResponseError;
import com.vellumhub.user_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
@ApiResponse(responseCode = "403", description = "Insufficient permission", content = @Content)
@RequestMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserPreferenceController {
    private final CreateUserPreferenceUseCase preferences;
    private final AuthenticationService authentication;

    public UserPreferenceController(CreateUserPreferenceUseCase preferences, AuthenticationService authentication) {
        this.preferences = preferences;
        this.authentication = authentication;
    }

    @GetMapping("/genres")
    @SecurityRequirement(name = "oauth2", scopes = "user:read")
    @Operation(summary = "List Catalog genres available for onboarding")
    @ApiResponse(responseCode = "200", description = "Catalog genres")
    public GenreSelection genres() {
        return new GenreSelection(CatalogGenreNames.ONBOARDING);
    }

    @GetMapping("/me/preferences")
    @SecurityRequirement(name = "oauth2", scopes = "user:read")
    @Operation(summary = "Read own genre preferences", description = "Returns an empty list before preferences are set.")
    @ApiResponse(responseCode = "200", description = "Current genre preferences")
    public GenreSelection getOwn() {
        return new GenreSelection(preferences.findGenres(authentication.getAuthenticatedUserId()));
    }

    @PutMapping("/me/preferences")
    @SecurityRequirement(name = "oauth2", scopes = "user:write")
    @Operation(summary = "Replace own genre preferences",
            description = "Use the exact genre names returned by GET /users/genres.")
    @ApiResponse(responseCode = "200", description = "Preferences saved")
    @ApiResponse(responseCode = "400", description = "Genres are empty, duplicate, or unknown",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponseError.class)))
    public GenreSelection replaceOwn(@Valid @RequestBody CreateUserPreferenceRequest request) {
        List<String> genres = request.genres();
        if (genres.stream().distinct().count() != genres.size() || !CatalogGenreNames.ONBOARDING.containsAll(genres)) {
            throw new UserPreferenceDomainException("Select distinct genres from the Catalog");
        }
        preferences.execute(new CreateUserPreferenceCommand(authentication.getAuthenticatedUserId(), genres, null));
        return new GenreSelection(genres);
    }

    public record GenreSelection(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> genres) {}
}
