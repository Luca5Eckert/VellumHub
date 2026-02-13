package com.mrs.engagement_service.module.rating.application.controller;

import com.mrs.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.handler.CreateRatingHandler;
import com.mrs.engagement_service.module.rating.application.handler.GetBookStatsHandler;
import com.mrs.engagement_service.module.rating.application.handler.GetUserRatingHandler;
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
    private final GetBookStatsHandler getBookStatsHandler;
    private final GetUserRatingHandler getUserRatingHandler;

    public RatingController(CreateRatingHandler createRatingHandler,
                            GetBookStatsHandler getBookStatsHandler,
                            GetUserRatingHandler getUserRatingHandler) {
        this.createRatingHandler = createRatingHandler;
        this.getBookStatsHandler = getBookStatsHandler;
        this.getUserRatingHandler = getUserRatingHandler;
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
        createRatingHandler.handle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("Rating registered successfully");
    }

    @GetMapping("/user/{userId}")
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

    @GetMapping("/media/{bookId}/stats")
    @Operation(summary = "Get media statistics", description = "Returns rating statistics for a specific book")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = GetBookStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Media not found", content = @Content)
    })
    public ResponseEntity<GetBookStatusResponse> getMediaStatus(@Parameter(description = "Book ID") @PathVariable UUID bookId) {
        GetBookStatusResponse response = getBookStatsHandler.handle(bookId);
        return ResponseEntity.ok(response);
    }
}