package com.vellumhub.user_service.module.auth.application.controller;

import com.vellumhub.user_service.module.auth.application.dto.LoginExternalRequest;
import com.vellumhub.user_service.module.auth.application.dto.LinkGoogleIdentityRequest;
import com.vellumhub.user_service.module.auth.application.dto.UnlinkGoogleIdentityRequest;
import com.vellumhub.security.jwt.JwtAccessToken;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.vellumhub.user_service.module.auth.application.dto.LoginUserRequest;
import com.vellumhub.user_service.module.auth.application.dto.RegisterUserRequest;
import com.vellumhub.user_service.module.auth.domain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and registration")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account in the system with the provided credentials")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data or email already in use", content = @Content)
    })
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates a user with email and password, returning a JWT token for subsequent API requests")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful. Returns JWT token",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    public ResponseEntity<String> login(@Valid @RequestBody LoginUserRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/google")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginExternalRequest request
    ) {
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/google/link")
    @Operation(summary = "Link a verified Google identity to the current password account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Linked or already linked"),
        @ApiResponse(responseCode = "401", description = "Missing/invalid local JWT"),
        @ApiResponse(responseCode = "403", description = "Current password proof required"),
        @ApiResponse(responseCode = "409", description = "External identity conflict")
    })
    public ResponseEntity<Void> linkGoogle(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody LinkGoogleIdentityRequest request) {
        authService.linkGoogle(JwtAccessToken.userId(jwt),
                request.externalToken(), request.currentPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/google/link")
    @Operation(summary = "Unlink Google from a local-password account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Unlinked or no matching identity"),
        @ApiResponse(responseCode = "403", description = "Current password proof required")
    })
    public ResponseEntity<Void> unlinkGoogle(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UnlinkGoogleIdentityRequest request) {
        authService.unlinkGoogle(JwtAccessToken.userId(jwt),
                request.currentPassword());
        return ResponseEntity.noContent().build();
    }
}

