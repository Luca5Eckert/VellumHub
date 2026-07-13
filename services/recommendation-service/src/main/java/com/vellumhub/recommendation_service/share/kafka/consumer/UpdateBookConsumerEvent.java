package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.UpdateBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.UpdateRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.UpdateRecommendationUseCase;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.UpdateBookEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookConsumerEvent {

    private static final String TOPIC = KafkaTopics.UPDATED_BOOK;
    private static final String EVENT_TYPE = "UpdateBookEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.RECOMMENDATION_SERVICE;

    private final UpdateBookFeatureUseCase updateBookFeatureUseCase;
    private final UpdateRecommendationUseCase updateRecommendationUseCase;
    private final VellumHubMetrics metrics;

    public UpdateBookConsumerEvent(UpdateBookFeatureUseCase updateBookFeatureUseCase, UpdateRecommendationUseCase updateRecommendationUseCase, VellumHubMetrics metrics) {
        this.updateBookFeatureUseCase = updateBookFeatureUseCase;
        this.updateRecommendationUseCase = updateRecommendationUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.UPDATED_BOOK,
            groupId = KafkaConsumerGroups.RECOMMENDATION_SERVICE
    )
    public void execute(UpdateBookEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("Event received: Book update. BookId={}, Genres={}",
                event.bookId(),
                event.genres());

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

        try {
            updateBookFeatureUseCase.execute(mediaFeatureCommand);
            updateRecommendationUseCase.execute(updateRecommendationCommand);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book update event processed successfully. BookId={}",
                event.bookId());

    }

}
