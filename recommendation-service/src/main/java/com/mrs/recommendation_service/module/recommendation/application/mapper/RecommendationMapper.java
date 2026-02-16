package com.mrs.recommendation_service.module.recommendation.application.mapper;

import com.mrs.recommendation_service.module.recommendation.application.dto.RecommendationResponse;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {

    public RecommendationResponse toResponse(
            Recommendation recommendation
    ){
        return new RecommendationResponse(
                recommendation.id(),
                recommendation.title(),
                recommendation.description(),
                recommendation.releaseYear(),
                recommendation.coverUrl(),
                recommendation.genres()
        );
    }

}
