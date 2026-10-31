package com.vellumhub.user_service.module.user.application.controller;

import com.vellumhub.user_service.module.user.application.dto.UpdateUserRequest;
import com.vellumhub.user_service.module.user.application.dto.UserGetResponse;
import com.vellumhub.user_service.module.user.domain.service.UserService;
import com.vellumhub.user_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Endpoints for user management")
public class UserController {

    private final UserService userService;

    private final AuthenticationService authenticationService;

    public UserController(UserService userService, AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "oauth2", scopes = {"user:read"})
    @Operation(summary = "Find own profile by ID", description = "Returns private profile data only when the requested ID matches the authenticated subject")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found successfully",
                    content = @Content(schema = @Schema(implementation = UserGetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<UserGetResponse> getById(@Parameter(description = "User unique ID") @PathVariable UUID id) {
        UserGetResponse user = userService.getOwned(id, authenticationService.getAuthenticatedUserId());
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @SecurityRequirement(name = "oauth2", scopes = {"user:read"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users", description = "ADMIN only: returns a paginated list of registered users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserGetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<UserGetResponse>> getAll(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        List<UserGetResponse> users = userService.getAll(page, size);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "oauth2", scopes = {"user:write"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Updates an existing user's data. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data provided", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden: Administrator permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> update(
            @Parameter(description = "User unique ID") @PathVariable UUID id,
            @RequestBody @Valid UpdateUserRequest updateUserRequest) {
        userService.update(id, updateUserRequest);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "oauth2", scopes = {"user:write"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Removes a user from the system. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden: Administrator permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "User unique ID") @PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "oauth2", scopes = {"user:read"})
    @Operation(summary = "Get authenticated user profile", description = "Returns the profile details of the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found successfully",
                    content = @Content(schema = @Schema(implementation = UserGetResponse.class))
            )
    })
    public ResponseEntity<UserGetResponse> getAuthenticated() {
        var userId = authenticationService.getAuthenticatedUserId();

        UserGetResponse user = userService.get(userId);

        return ResponseEntity.ok(user);
    }

}