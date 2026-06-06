package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.UpdateBookProgressEvent;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookProgressConsumerEvent {

    private static final String TOPIC = "updated-reading-progress";
    private static final String EVENT_TYPE = "UpdateBookProgressEvent";
    private static final String CONSUMER_GROUP = "recommendation-service";

    private final UpdateBookProgressUseCase updateBookProgressUseCase;
    private final VellumHubMetrics metrics;

    public UpdateBookProgressConsumerEvent(UpdateBookProgressUseCase updateBookProgressUseCase, VellumHubMetrics metrics) {
        this.updateBookProgressUseCase = updateBookProgressUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "updated-reading-progress",
            groupId = "recommendation-service"
    )
    public void consume(
            UpdateBookProgressEvent event
    ) {
        Timer.Sample sample = metrics.startKafkaProcessing();

        log.info("Event received: Update book progress. UserId={}, BookId={}, Progress={}",
                event.userId(),
                event.bookId(),
                event.progress()
        );

        var command = UpdateBookProgressCommand.of(
                event.userId(),
                event.bookId(),
                event.progress(),
                event.oldPage(),
                event.newPage()
        );

        try {
            updateBookProgressUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book progress update event processed successfully. UserId={}, BookId={}",
                event.userId(),
                event.bookId()
        );
    }

}
