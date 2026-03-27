package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.recommendation.application.command.GetRecommendationsCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetRecommendationsUseCase {

    private final BookFeatureRepository bookFeatureRepository;
    private final RecommendationRepository recommendationRepository;

    public GetRecommendationsUseCase(BookFeatureRepository bookFeatureRepository, RecommendationRepository recommendationRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.recommendationRepository = recommendationRepository;
    }

    public List<Recommendation> execute(GetRecommendationsCommand command) {
        List<UUID> booksId = bookFeatureRepository.findAllByUserId(
                command.userId(),
                command.limit(),
                command.offset()
        );

        if(booksId == null || booksId.isEmpty()){
            booksId = bookFeatureRepository.findMostPopularMedias(command.limit(), command.offset());
        }

        return recommendationRepository.findAllById(booksId);
    }

}
