package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.consumer;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.event.UpdateBookProgressEvent;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateReadingProgressEventConsumer {

    private static final String TOPIC = "updated-reading-progress";
    private static final String EVENT_TYPE = "UpdateBookProgressEvent";
    private static final String CONSUMER_GROUP = "engagement-service";

    private final CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;
    private final VellumHubMetrics metrics;

    public UpdateReadingProgressEventConsumer(CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase, VellumHubMetrics metrics) {
        this.createReadingSessionEntryUseCase = createReadingSessionEntryUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "updated-reading-progress",
            groupId = "engagement-service"
    )
    public void consume(UpdateBookProgressEvent event){
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info(
                "Received UpdateBookProgressEvent. operation=kafka_consume, topic=updated-reading-progress, event_type=UpdateBookProgressEvent, userId={}, bookId={}, progress={}, page={}",
                event.userId(),
                event.bookId(),
                event.progress(),
                event.newPage()
        );

        var command = CreateReadingSessionEntryCommand.create(
                event.bookId(),
                event.bookProgressId(),
                event.userId(),
                event.progress(),
                event.newPage()
        );

        log.info(
                "Executing CreateReadingSessionEntryCommand. operation=create_reading_session_entry, event_type=UpdateBookProgressEvent, userId={}, bookId={}, page={}",
                event.userId(),
                event.bookId(),
                event.newPage()
        );

        try {
            createReadingSessionEntryUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Finished processing UpdateBookProgressEvent for userId: {}, bookId: {}", event.userId(), event.bookId());
    }

}
