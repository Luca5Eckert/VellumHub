package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.recommendation.application.command.GetRecommendationsCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsUseCase {

    private final BookFeatureRepository bookFeatureRepository;
    private final RecommendationRepository recommendationRepository;
    private final VellumHubMetrics metrics;

    public GetRecommendationsUseCase(BookFeatureRepository bookFeatureRepository, RecommendationRepository recommendationRepository, VellumHubMetrics metrics) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.recommendationRepository = recommendationRepository;
        this.metrics = metrics;
    }

    public List<Recommendation> execute(GetRecommendationsCommand command) {
        var sample = metrics.startBusinessTimer();

        try {
            List<UUID> booksId = bookFeatureRepository.findAllByUserId(
                    command.userId(),
                    command.limit(),
                    command.offset()
            );

            if(booksId == null || booksId.isEmpty()){
                booksId = bookFeatureRepository.findMostPopularMedias(command.limit(), command.offset());
            }

            List<Recommendation> recommendations = recommendationRepository.findAllById(booksId);

            if (recommendations == null || recommendations.isEmpty()) {
                metrics.recordBusinessCounter(VellumHubMetrics.RECOMMENDATION_EMPTY_RESULTS, "recommendation_generation", "empty");
                metrics.recordRecommendationGenerationDuration(sample, "empty");
            } else {
                metrics.recordBusinessCounter(VellumHubMetrics.RECOMMENDATIONS_GENERATED, "recommendation_generation", "success");
                metrics.recordRecommendationGenerationDuration(sample, "success");
            }

            return recommendations;
        } catch (RuntimeException exception) {
            metrics.recordRecommendationGenerationDuration(sample, "failure");
            throw exception;
        }
    }

}
