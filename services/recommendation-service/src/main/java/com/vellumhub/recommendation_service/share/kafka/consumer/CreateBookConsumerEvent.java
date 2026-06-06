package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.CreateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.CreateRecommendationUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import com.vellumhub.recommendation_service.share.kafka.event.CreateBookEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class CreateBookConsumerEvent {

    private static final String TOPIC = "created-book";
    private static final String EVENT_TYPE = "CreateBookEvent";
    private static final String CONSUMER_GROUP = "recommendation-service";

    private final CreateBookFeatureUseCase createBookFeatureUseCase;
    private final CreateRecommendationUseCase createRecommendationUseCase;
    private final VellumHubMetrics metrics;

    public CreateBookConsumerEvent(CreateBookFeatureUseCase createBookFeatureUseCase, CreateRecommendationUseCase createRecommendationUseCase, VellumHubMetrics metrics) {
        this.createBookFeatureUseCase = createBookFeatureUseCase;
        this.createRecommendationUseCase = createRecommendationUseCase;
        this.metrics = metrics;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    @Transactional
    public void listen(CreateBookEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
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
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book creation event processed successfully. BookId={}",
                event.bookId());


    }

}
