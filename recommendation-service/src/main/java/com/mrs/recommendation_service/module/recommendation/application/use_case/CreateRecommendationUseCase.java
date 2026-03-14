package com.mrs.recommendation_service.module.recommendation.application.use_case;

import com.mrs.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.mrs.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateRecommendationUseCase {

    private final RecommendationRepository recommendationRepository;

    public CreateRecommendationUseCase(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    public void execute(CreateRecommendationCommand command){
        Recommendation recommendation = Recommendation.builder()
                .bookId(command.bookId())
                .title(command.title())
                .description(command.description())
                .author(command.author())
                .coverUrl(command.coverUrl())
                .releaseYear(command.releaseYear())
                .genres(command.genres())
                .build();

        recommendationRepository.save(recommendation);
    }

}
