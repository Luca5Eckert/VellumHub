package com.mrs.engagement_service.module.rating.application.controller;

import com.mrs.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.mrs.engagement_service.module.rating.application.handler.CreateRatingHandler;
import com.mrs.engagement_service.module.rating.application.handler.GetUserRatingHandler;
import com.mrs.engagement_service.module.rating.application.handler.UpdateRatingHandler;
import com.mrs.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
import com.mrs.engagement_service.share.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rating")
@Tag(name = "Rating", description = "Endpoints for managing user ratings of media")
public class RatingController {

    private final CreateRatingHandler createRatingHandler;
    private final GetUserRatingHandler getUserRatingHandler;
    private final UpdateRatingHandler updateRatingHandler;

    private final AuthenticationService authenticationService;

    public RatingController(
            CreateRatingHandler createRatingHandler,
            GetUserRatingHandler getUserRatingHandler, UpdateRatingHandler updateRatingHandler, AuthenticationService authenticationService
    ) {
        this.createRatingHandler = createRatingHandler;
        this.getUserRatingHandler = getUserRatingHandler;
        this.updateRatingHandler = updateRatingHandler;
        this.authenticationService = authenticationService;
    }

    @PostMapping
    @Operation(summary = "Register rating", description = "Registers a new user rating for a media (0-5 stars + review)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rating registered successfully",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Invalid data", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<String> create(@RequestBody @Valid CreateRatingRequest request) {
        var userId = authenticationService.getAuthenticatedUserId();

        createRatingHandler.handle(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body("Rating registered successfully");
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user ratings", description = "Returns all ratings from a specific user with optional filters")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of ratings retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RatingGetResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<RatingGetResponse>> findAllOfUser(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Filter by minimum stars") @RequestParam(required = false) Integer minStars,
            @Parameter(description = "Filter by maximum stars") @RequestParam(required = false) Integer maxStars,
            @Parameter(description = "Start date (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "End date (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<RatingGetResponse> response = getUserRatingHandler.handle(
                userId, minStars, maxStars, from, to, pageNumber, pageSize
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<RatingGetResponse>> findAllOfUserAuthenticated(
            @Parameter(description = "Filter by minimum stars") @RequestParam(required = false) Integer minStars,
            @Parameter(description = "Filter by maximum stars") @RequestParam(required = false) Integer maxStars,
            @Parameter(description = "Start date (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "End date (ISO 8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int pageSize
    ){
      var userId = authenticationService.getAuthenticatedUserId();

        List<RatingGetResponse> response = getUserRatingHandler.handle(
                userId, minStars, maxStars, from, to, pageNumber, pageSize
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{ratingId}")
    @Operation(summary = "Update rating", description = "Updates an existing user rating for a media (0-5 stars + review)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    content = @Content(schema = @Schema(implementation = RatingGetResponse.class))
            )
    })
    public ResponseEntity<RatingGetResponse> update(
            @Parameter(description = "Rating ID") @PathVariable Long ratingId,
            @RequestBody UpdateRatingRequest request
    ) {
        var userId = authenticationService.getAuthenticatedUserId();

        var response = updateRatingHandler.handle(ratingId, request);

        return ResponseEntity.ok(response);
    }

    
}