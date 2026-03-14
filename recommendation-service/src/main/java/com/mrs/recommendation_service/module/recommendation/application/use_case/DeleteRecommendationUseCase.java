package com.mrs.recommendation_service.module.recommendation.application.use_case;

import com.mrs.recommendation_service.module.recommendation.application.command.DeleteRecommendationCommand;
import com.mrs.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteRecommendationUseCase {

    private final RecommendationRepository recommendationRepository;

    public DeleteRecommendationUseCase(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    public void execute(DeleteRecommendationCommand command) {
        recommendationRepository.deleteById(command.bookId());
    }
}
