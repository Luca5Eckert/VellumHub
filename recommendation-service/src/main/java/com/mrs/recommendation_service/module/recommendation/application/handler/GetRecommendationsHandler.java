package com.mrs.recommendation_service.module.recommendation.application.handler;

import com.mrs.recommendation_service.module.recommendation.application.dto.RecommendationResponse;
import com.mrs.recommendation_service.module.recommendation.application.mapper.RecommendationMapper;
import com.mrs.recommendation_service.module.recommendation.domain.command.GetRecommendationsCommand;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.mrs.recommendation_service.module.recommendation.domain.use_case.GetRecommendationsUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsHandler {

    private final GetRecommendationsUseCase getRecommendationsUseCase;
    private final RecommendationMapper recommendationMapper;

    public GetRecommendationsHandler(GetRecommendationsUseCase getRecommendationsUseCase, RecommendationMapper recommendationMapper) {
        this.getRecommendationsUseCase = getRecommendationsUseCase;
        this.recommendationMapper = recommendationMapper;
    }

    public List<RecommendationResponse> handle(UUID userId, int limit, int offset) {
        GetRecommendationsCommand command = new GetRecommendationsCommand(
                userId,
                limit,
                offset
        );

        List<Recommendation> recommendations = getRecommendationsUseCase.execute(command);

        return recommendations.stream()
                .map(recommendationMapper::toResponse)
                .toList();
    }
}
