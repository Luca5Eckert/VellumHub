package com.mrs.recommendation_service.module.recommendation.application.use_case;

import com.mrs.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.mrs.recommendation_service.module.recommendation.application.command.UpdateRecommendationCommand;
import com.mrs.recommendation_service.module.recommendation.domain.exception.RecommendationDomainException;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.mrs.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateRecommendationUseCase {

    private final RecommendationRepository recommendationRepository;

    public UpdateRecommendationUseCase(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    public void execute(UpdateRecommendationCommand command){
        Recommendation recommendation = recommendationRepository.findById(command.bookId())
                .orElseThrow(() -> new RecommendationDomainException("Recommendation not found"));

        recommendation.update(
                command.title(),
                command.description(),
                command.author(),
                command.coverUrl(),
                command.releaseYear(),
                command.genres()
        );

        recommendationRepository.save(recommendation);
    }

}
