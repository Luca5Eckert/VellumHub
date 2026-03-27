package com.mrs.recommendation_service.module.recommendation.presentation.mapper;

import com.mrs.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {

    public RecommendationResponse toResponse(
            Recommendation recommendation
    ){
        return new RecommendationResponse(
                recommendation.getBookId(),
                recommendation.getTitle(),
                recommendation.getDescription(),
                recommendation.getReleaseYear(),
                recommendation.getCoverUrl(),
                recommendation.getAuthor(),
                recommendation.getGenres()
        );
    }

}
