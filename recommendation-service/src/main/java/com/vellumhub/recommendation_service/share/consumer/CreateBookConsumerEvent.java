package com.vellumhub.recommendation_service.share.consumer;

import com.vellumhub.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.CreateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.event.CreateBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class CreateBookConsumerEvent {

    private final CreateBookFeatureUseCase createBookFeatureUseCase;
    private final CreateRecommendationUseCase createRecommendationUseCase;

    public CreateBookConsumerEvent(CreateBookFeatureUseCase createBookFeatureUseCase, CreateRecommendationUseCase createRecommendationUseCase) {
        this.createBookFeatureUseCase = createBookFeatureUseCase;
        this.createRecommendationUseCase = createRecommendationUseCase;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    @Transactional
    public void listen(CreateBookEvent event){
        log.info("Event received: Book creation. BookId={}, Genres={}",
                event.bookId(),
                event.genres());

        try {
            createBookFeatureUseCase.execute(event);

            CreateRecommendationCommand command = CreateRecommendationCommand.of(
                    event.bookId(),
                    event.title(),
                    event.description(),
                    event.releaseYear(),
                    event.coverUrl(),
                    event.author(),
                    event.genres()
            );
            createRecommendationUseCase.execute(command);

            log.info("Book creation event processed successfully. BookId={}",
                    event.bookId());

        } catch (Exception e) {
            log.error("Error processing book creation event. BookId={}, Genres={}",
                    event.bookId(),
                    event.genres(),
                    e);
            throw e;
        }
    }

}
