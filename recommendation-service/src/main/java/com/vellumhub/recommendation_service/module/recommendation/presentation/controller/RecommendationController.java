package com.vellumhub.recommendation_service.module.recommendation.presentation.controller;

import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import com.vellumhub.recommendation_service.module.recommendation.presentation.mapper.RecommendationMapper;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.GetRecommendationsUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.GetRecommendationsCommand;
import com.vellumhub.recommendation_service.share.provider.UserAuthenticationProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/recommendations")
@Tag(name = "Recommendations", description = "Endpoints for obtaining personalized book recommendations")
public class RecommendationController {

    private final UserAuthenticationProvider userAuthenticationProvider;
    private final RecommendationMapper mapper;

    private final GetRecommendationsUseCase getRecommendationsUseCase;

    public RecommendationController(UserAuthenticationProvider userAuthenticationProvider, RecommendationMapper mapper, GetRecommendationsUseCase getRecommendationsUseCase) {
        this.userAuthenticationProvider = userAuthenticationProvider;
        this.mapper = mapper;
        this.getRecommendationsUseCase = getRecommendationsUseCase;
    }
    /**
     * Returns recommendations for the authenticated user.
     *
     * @param limit  Maximum number of recommendations to return
     * @param offset Number of items to skip
     * @return List of user recommendations
     */
    @GetMapping
    @Operation(summary = "Get book recommendations", description = "Returns personalized book recommendations for the authenticated user based on their rating history and reading preferences")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommendations successfully retrieved",
                    content = @Content(schema = @Schema(implementation = RecommendationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @Parameter(description = "Maximum number of recommendations to return") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Number of recommendations to skip for pagination") @RequestParam(defaultValue = "0") int offset
    ) {

        var userId = userAuthenticationProvider.getUserId();

        GetRecommendationsCommand command = new GetRecommendationsCommand(
                userId,
                limit,
                offset
        );

        var recommendations = getRecommendationsUseCase.execute(command);
        var response = recommendations.stream()
                .map(mapper::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }
}