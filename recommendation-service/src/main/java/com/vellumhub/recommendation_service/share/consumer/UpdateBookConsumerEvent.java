package com.vellumhub.recommendation_service.share.consumer;

import com.vellumhub.recommendation_service.module.recommendation.application.command.UpdateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.UpdateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.event.UpdateBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.UpdateMediaFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookConsumerEvent {

    private final UpdateMediaFeatureUseCase updateMediaFeatureUseCase;
    private final UpdateRecommendationUseCase updateRecommendationUseCase;

    public UpdateBookConsumerEvent(UpdateMediaFeatureUseCase updateMediaFeatureUseCase, UpdateRecommendationUseCase updateRecommendationUseCase) {
        this.updateMediaFeatureUseCase = updateMediaFeatureUseCase;
        this.updateRecommendationUseCase = updateRecommendationUseCase;
    }

    @KafkaListener(
            topics = "updated-book",
            groupId = "recommendation-service"
    )
    public void execute(UpdateBookEvent event){
        log.info("Event received: Book update. BookId={}, Genres={}",
                event.bookId(),
                event.genres());

        try {
            UpdateBookFeatureCommand mediaFeatureCommand = UpdateBookFeatureCommand.of(
                    event.bookId(),
                    event.title(),
                    event.author(),
                    event.description(),
                    event.genres()
            );
            UpdateRecommendationCommand updateRecommendationCommand = UpdateRecommendationCommand.of(
                    event.bookId(),
                    event.title(),
                    event.description(),
                    event.releaseYear(),
                    event.coverUrl(),
                    event.author(),
                    event.genres()
            );

            updateMediaFeatureUseCase.execute(mediaFeatureCommand);
            updateRecommendationUseCase.execute(updateRecommendationCommand);

            log.info("Book update event processed successfully. BookId={}",
                    event.bookId());

        } catch (Exception e) {
            log.error("Error processing book update event. BookId={}, Genres={}",
                    event.bookId(),
                    event.genres(),
                    e);
            throw e;
        }
    }

}
