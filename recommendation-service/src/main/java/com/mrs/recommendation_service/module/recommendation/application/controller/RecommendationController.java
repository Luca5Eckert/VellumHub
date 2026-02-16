package com.mrs.recommendation_service.module.recommendation.application.controller;

import com.mrs.recommendation_service.module.recommendation.application.dto.RecommendationResponse;
import com.mrs.recommendation_service.module.recommendation.application.handler.GetRecommendationsHandler;
import com.mrs.recommendation_service.share.provider.UserAuthenticationProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Endpoints for obtaining personalized media recommendations")
public class RecommendationController {

    private final UserAuthenticationProvider userAuthenticationProvider;

    private final GetRecommendationsHandler getRecommendationsHandler;

    public RecommendationController(UserAuthenticationProvider userAuthenticationProvider, GetRecommendationsHandler getRecommendationsHandler) {
        this.userAuthenticationProvider = userAuthenticationProvider;
        this.getRecommendationsHandler = getRecommendationsHandler;
    }

    /**
     * Returns recommendations for the authenticated user.
     *
     * @param limit  Maximum number of recommendations to return
     * @param offset Number of items to skip
     * @return List of user recommendations
     */
    @GetMapping
    @Operation(summary = "Get recommendations", description = "Returns personalized recommendations for the authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations successfully retrieved",
                    content = @Content(schema = @Schema(implementation = RecommendationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {

        var userId = userAuthenticationProvider.getUserId();

        var recommendations = getRecommendationsHandler.handle(
                userId,
                limit,
                offset
        );

        return ResponseEntity.ok(recommendations);
    }
}