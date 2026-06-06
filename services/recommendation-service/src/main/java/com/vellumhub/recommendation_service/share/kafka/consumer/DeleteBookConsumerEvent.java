package com.vellumhub.recommendation_service.share.kafka.consumer;

import com.vellumhub.recommendation_service.module.book_feature.application.use_case.DeleteBookFeatureUseCase;
import com.vellumhub.recommendation_service.module.recommendation.application.command.DeleteRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.DeleteRecommendationUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import com.vellumhub.recommendation_service.share.kafka.event.DeleteBookEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeleteBookConsumerEvent {

    private static final String TOPIC = "deleted-book";
    private static final String EVENT_TYPE = "DeleteBookEvent";
    private static final String CONSUMER_GROUP = "recommendation-service";

    private final DeleteBookFeatureUseCase deleteBookFeatureUseCase;
    private final DeleteRecommendationUseCase deleteRecommendationUseCase;
    private final VellumHubMetrics metrics;

    public DeleteBookConsumerEvent(DeleteBookFeatureUseCase deleteBookFeatureUseCase, DeleteRecommendationUseCase deleteRecommendationUseCase, VellumHubMetrics metrics) {
        this.deleteBookFeatureUseCase = deleteBookFeatureUseCase;
        this.deleteRecommendationUseCase = deleteRecommendationUseCase;
        this.metrics = metrics;
    }


    @KafkaListener(
            topics = "deleted-book",
            groupId = "recommendation-service"
    )
    public void listen(DeleteBookEvent deleteBookEvent) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("Event received: Book deletion. BookId={}",
                deleteBookEvent.bookId());

        try {
            deleteBookFeatureUseCase.execute(deleteBookEvent.bookId());

            var command = DeleteRecommendationCommand.of(deleteBookEvent.bookId());
            deleteRecommendationUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book deletion event processed successfully. BookId={}",
                deleteBookEvent.bookId());

    }

}
